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
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.rest.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AbTestService extends AbstractLifecycleComponent<AbTestService> {
    protected static final String TEST_SETTING_INDEX = ".abtest";
    protected static final String TEST_INDEX_NAME_FIELD = "test_index_name";

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

    public void getTestCaseIndex(final String originalIndex, final String rt, final Consumer<String> consumer) {
        if(!validateRt(rt)) {
            consumer.accept(originalIndex);
            return;
        }

        client.prepareGet()
            .setIndex(TEST_SETTING_INDEX)
            .setType(originalIndex)
            .setId(String.valueOf(rt))
            .execute(new ActionListener<GetResponse>() {
                @Override
                public void onResponse(GetResponse getResponse) {
                    String index = originalIndex;
                    if (getResponse.isExists()) {
                        final GetField field = getResponse.getField(TEST_INDEX_NAME_FIELD);
                        if (field != null) {
                            index = field.getValue().toString();
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

    public void updateTestSweets(final String index, final List<Map<String, Object>> testSweets,
                                 final Consumer<Boolean> success, final Consumer<Throwable> error) {
        final BulkRequest bulkRequest = new BulkRequest();

        int testCount = 0;
        for(final Map<String, Object> map: testSweets) {
            final TestSweet testSweet = TestSweet.parse(map);
            bulkRequest.add(createIndexRequest(index, testSweet, String.valueOf(testCount++)));
            if(MAX_TEST_NUMBER < testCount) {
                throw new IllegalArgumentException("Too many testcase.");
            }
        }

        client.bulk(bulkRequest, new ActionListener<BulkResponse>() {
            @Override
            public void onResponse(BulkResponse bulkItemResponses) {
                if(bulkItemResponses.hasFailures()) {
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

    protected IndexRequest createIndexRequest(final String index, final TestSweet testSweet, final String id) {
        final IndexRequest indexRequest = new IndexRequest();
        indexRequest.index(TEST_SETTING_INDEX).type(index).id(id)
            .source(testSweet.source());
        return indexRequest;
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


    public static class TestSweet {
        public String testName;
        public String testIndexName;
        public int percentage;

        private TestSweet() {

        }

        public TestSweet(final String testName, final String testIndexName, final int percentage) {
            this.testName = testName;
            this.testIndexName = testIndexName;
            this.percentage = percentage;
        }

        public Map<String, Object> source() {
            final Map<String, Object> source = new HashMap<>();
            source.put("test_name", testName);
            source.put("test_index_name", testIndexName);
            return source;
        }

        public static TestSweet parse(final Map<String, Object> testSweet) {
            final TestSweet instance = new TestSweet();

            final Object testNameObj = testSweet.get("test_name");
            if(testNameObj == null) {
                throw new IllegalArgumentException("test_name was null.");
            }
            instance.testName = testNameObj.toString();

            final Object testIndexNameObj = testSweet.get("test_index_name");
            if(testIndexNameObj == null) {
                throw new IllegalArgumentException("test_index_name was null.");
            }
            instance.testIndexName = testIndexNameObj.toString();

            final Object percentageObj = testSweet.get("percentage");
            if(percentageObj == null) {
                throw new IllegalArgumentException("percentage was null");
            }
            instance.percentage = Integer.parseInt(percentageObj.toString());

            return instance;
        }
    }

}
