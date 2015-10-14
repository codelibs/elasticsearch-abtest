package org.codelibs.elasticsearch.abtest.service;

import org.codelibs.elasticsearch.abtest.exception.AbTestException;
import org.codelibs.elasticsearch.abtest.filter.rest.PutParamToContextRestFilter;
import org.codelibs.elasticsearch.abtest.filter.transport.AbTestSearchActionFilter;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AbTestService extends AbstractLifecycleComponent<AbTestService> {
    protected static final String TEST_SETTING_INDEX = ".abtest";

    protected static final int MIN_TEST_NUMBER = 0;
    protected static final int MAX_TEST_NUMBER = 99;

    protected final Client client;
    protected final RestController restController;

    @Inject
    public AbTestService(final Settings settings, final Client client,
                         final RestController restController, final ActionFilters actionFilters) {
        super(settings);
        logger.info("CREATE AbTestService");

        this.client = client;
        this.restController = restController;

        for(final ActionFilter filter: actionFilters.filters()) {
            if(filter instanceof AbTestSearchActionFilter) {
                ((AbTestSearchActionFilter) filter).injectService(this);
            }
        }
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        logger.info("START AbTestService");

        final PutParamToContextRestFilter filter = new PutParamToContextRestFilter();
        restController.registerFilter(filter);
    }

    @Override
    protected void doStop() throws ElasticsearchException {
        logger.info("STOP AbTestService");
    }

    @Override
    protected void doClose() throws ElasticsearchException {
        logger.info("CLOSE AbTestService");
    }

    public void rewriteIndex(final String originalIndex, final String rt, final Consumer<String> consumer) {
        if(!validateRt(rt)) {
            consumer.accept(originalIndex);
            return;
        }

        final String testSweetName = normalizeTestSweetName(originalIndex);

        client.prepareGet()
            .setIndex(TEST_SETTING_INDEX)
            .setType(testSweetName)
            .setId(String.valueOf(rt))
            .execute(new ActionListener<GetResponse>() {
                @Override
                public void onResponse(GetResponse getResponse) {
                    String index = originalIndex;
                    if (getResponse.isExists()) {
                        final Map<String, Object> source = getResponse.getSourceAsMap();
                        final Object indexObj = source.get(TestCase.FIELD_TEST_INDEX);
                        if (indexObj != null) {
                            index = indexObj.toString();
                        }
                    }
                    consumer.accept(index);
                }

                @Override
                public void onFailure(Throwable e) {
                    logger.error("Failed to get testcase.", e);
                    consumer.accept(originalIndex);
                }
            });
    }

    public String convertTestCaseKey(final String str) {
        return String.valueOf(str.hashCode() % 100);
    }

    public void updateTestSweet(final String testSweetName, final List<Map<String, Object>> testCases,
                                 final Consumer<Boolean> success, final Consumer<Throwable> error) {
        final String normalizedTestSweetName = normalizeTestSweetName(testSweetName);
        final BulkRequest bulkRequest = new BulkRequest();

        int testCount = 0;
        for(final Map<String, Object> map: testCases) {
            final TestCase testCase = TestCase.parse(map);
            for(int i=0; i<testCase.percentage; i++) {
                bulkRequest.add(createIndexRequest(normalizedTestSweetName, testCase, testCount++));
            }
            if(MAX_TEST_NUMBER < testCount) {
                throw new IllegalArgumentException("Too many testcase.");
            }
        }

        for(; testCount <= MAX_TEST_NUMBER; testCount++) {
            bulkRequest.add(createDeleteRequest(normalizedTestSweetName, testCount++));
        }

        client.bulk(bulkRequest, new ActionListener<BulkResponse>() {
            @Override
            public void onResponse(BulkResponse bulkItemResponses) {
                if (bulkItemResponses.hasFailures()) {
                    error.accept(new AbTestException());
                } else {
                    success.accept(true);
                }
            }

            @Override
            public void onFailure(Throwable e) {
                error.accept(e);
            }
        });
    }

    public void deleteTestSweet(final String testSweetName, final Consumer<Boolean> success, final Consumer<Throwable> error) {

        client.prepareDelete().setIndex(TEST_SETTING_INDEX).setType(normalizeTestSweetName(testSweetName))
            .execute(new ActionListener<DeleteResponse>() {
                @Override
                public void onResponse(DeleteResponse deleteResponse) {
                    success.accept(true);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    error.accept(throwable);
                }
            });
    }

    public void getTestSweet(final String testSweetName, final Consumer<List<TestCase>> success, final Consumer<Throwable> error) {
        client.prepareSearch(TEST_SETTING_INDEX).setTypes(normalizeTestSweetName(testSweetName)).addSort(TestCase.FIELD_ID, SortOrder.ASC).setSize(MAX_TEST_NUMBER - MIN_TEST_NUMBER + 1)
            .execute(new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    final List<TestCase> testCaseList = new ArrayList<>();
                    final SearchHit[] hits = response.getHits().getHits();
                    if (hits.length > 0) {
                        for (final SearchHit hit : hits) {
                            final Map<String, Object> source = hit.sourceAsMap();
                            final String testName = source.get(TestCase.FIELD_TEST_NAME).toString();
                            final String testIndexName = source.get(TestCase.FIELD_TEST_INDEX).toString();

                            boolean contain = false;
                            for (final TestCase testCase : testCaseList) {
                                if (testCase.testName.equals(testName)) {
                                    testCase.percentage++;
                                    contain = true;
                                    break;
                                }
                            }
                            if (!contain) {
                                testCaseList.add(new TestCase(testName, testIndexName, 1));
                            }
                        }
                    }

                    success.accept(testCaseList);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    error.accept(throwable);
                }
            });
    }

    protected boolean validateRt(final String rt) {
        try {
            int num = Integer.parseInt(rt);
            if(num < MIN_TEST_NUMBER || MAX_TEST_NUMBER < num) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    protected IndexRequest createIndexRequest(final String testSweetName, final TestCase testCase, final int id) {
        final IndexRequest indexRequest = new IndexRequest();
        return indexRequest.index(TEST_SETTING_INDEX).type(testSweetName).id(String.valueOf(id))
            .source(testCase.source(id));
    }

    protected DeleteRequest createDeleteRequest(final String testSweetName, final int id) {
        final DeleteRequest deleteRequest = new DeleteRequest();
        return deleteRequest.index(TEST_SETTING_INDEX).type(testSweetName).id(String.valueOf(id));
    }

    protected String normalizeTestSweetName(final String testSweet) {
        return testSweet.replace(".", "_").toLowerCase();
    }


    protected void createSettingIndexIfNothing() throws IOException {
        final IndicesExistsResponse response =
            client.admin().indices().prepareExists(TEST_SETTING_INDEX).execute().actionGet(1, TimeUnit.MINUTES);
        if(!response.isExists()) {
            BufferedReader br = null;
            final StringBuilder sb = new StringBuilder();
            try {
                br =
                    new BufferedReader(new InputStreamReader(this.getClass().getClassLoader()
                        .getResourceAsStream("test-index-mapping.json")));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            } finally {
                if (br != null) {
                    br.close();
                }
            }
            client.admin().indices().prepareCreate(TEST_SETTING_INDEX)
                .addMapping(sb.toString()).execute(new ActionListener<CreateIndexResponse>() {
                    @Override
                    public void onResponse(final CreateIndexResponse response) {
                        //ignore
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        logger.error("Failed to create test settings index.");
                    }
            });
        }
    }


    public static class TestCase {
        public static final String FIELD_TEST_NAME = "test_name";
        public static final String FIELD_TEST_INDEX = "index";
        public static final String FIELD_PERCENTAGE = "percentage";
        public static final String FIELD_ID = "test_id";

        public String testName;
        public String testIndexName;
        public int percentage;

        private TestCase() {

        }

        public TestCase(final String testName, final String testIndexName, final int percentage) {
            this.testName = testName;
            this.testIndexName = testIndexName;
            this.percentage = percentage;
        }

        public Map<String, Object> source(final int id) {
            final Map<String, Object> source = new HashMap<>();
            source.put(FIELD_TEST_NAME, testName);
            source.put(FIELD_TEST_INDEX, testIndexName);
            source.put(FIELD_ID, id);
            return source;
        }

        public static TestCase parse(final Map<String, Object> testSweet) {
            final TestCase instance = new TestCase();

            final Object testNameObj = testSweet.get(FIELD_TEST_NAME);
            if(testNameObj == null) {
                throw new IllegalArgumentException("test_name was null.");
            }
            instance.testName = testNameObj.toString();

            final Object testIndexNameObj = testSweet.get(FIELD_TEST_INDEX);
            if(testIndexNameObj == null) {
                throw new IllegalArgumentException("test_index_name was null.");
            }
            instance.testIndexName = testIndexNameObj.toString();

            final Object percentageObj = testSweet.get(FIELD_PERCENTAGE);
            if(percentageObj == null) {
                throw new IllegalArgumentException("percentage was null");
            }
            instance.percentage = Integer.parseInt(percentageObj.toString());

            return instance;
        }
    }

}
