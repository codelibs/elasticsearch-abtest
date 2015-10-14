package org.codelibs.elasticsearch.abtest;

import org.codelibs.elasticsearch.abtest.service.AbTestService;
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.codelibs.elasticsearch.runner.net.Curl;
import org.codelibs.elasticsearch.runner.net.CurlResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import static org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner.newConfigs;
import static org.junit.Assert.*;

public class AbTestPluginTest {
    static ElasticsearchClusterRunner runner;

    @BeforeClass
    public static void beforeClass() throws Exception {
        runner = new ElasticsearchClusterRunner();
        runner.onBuild((number, settingsBuilder) -> {
            settingsBuilder.put("http.cors.enabled", true);
            settingsBuilder.put("index.number_of_replicas", 0);
            settingsBuilder.put("script.disable_dynamic", false);
            settingsBuilder.put("script.groovy.sandbox.enabled", true);
            settingsBuilder.put("fsuggest.ngquery", "k,ken");
        }).build(newConfigs().ramIndexStore().numOfNode(1));
        runner.ensureYellow();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        runner.close();
        runner.clean();
    }

    @Before
    public void before() throws Exception {
        runner.deleteIndex("_all");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_addTestCase() throws Exception {
        final String index = "sample";
        updateSetting(index, "sample-settings.json");

        CurlResponse response = Curl.get(runner.masterNode(), "/" + index + "/_abtest/settings")
            .execute();
        final Map<String, Object> map = response.getContentAsMap();
        final List<Map<String, Object>> testCases = (List)map.get("testcases");
        assertEquals(2, testCases.size());
        assertEquals("test1", testCases.get(0).get(AbTestService.TestCase.FIELD_TEST_NAME));
        assertEquals("index_a", testCases.get(0).get(AbTestService.TestCase.FIELD_TEST_INDEX));
        assertEquals(10, testCases.get(0).get(AbTestService.TestCase.FIELD_PERCENTAGE));
        assertEquals("test2", testCases.get(1).get(AbTestService.TestCase.FIELD_TEST_NAME));
        assertEquals("index_b", testCases.get(1).get(AbTestService.TestCase.FIELD_TEST_INDEX));
        assertEquals(20, testCases.get(1).get(AbTestService.TestCase.FIELD_PERCENTAGE));
    }

    @Test
    public void test_abtest() throws Exception {
        final String index = "sample";
        final String indexA = "index_a";
        final String indexB = "index_b";

        updateSetting(index, "sample-settings.json");

        createDummyIndex(index, 1);
        createDummyIndex(indexA, 10);
        createDummyIndex(indexB, 100);

        for(int i=0; i<100; i++) {
            CurlResponse response = Curl.get(runner.masterNode(), "/" + index + "/_search")
                .param("q", "*:*")
                .param("ab_rt", String.valueOf(i))
                .param("hash_rt", "false")
                .execute();
            final int total = (int)((Map)response.getContentAsMap().get("hits")).get("total");
            if(i < 10) {
                assertEquals(10, total);
            } else if(i < 30) {
                assertEquals(100, total);
            } else {
                assertEquals(1, total);
            }
        }
    }

    protected void updateSetting(final String index, final String json) throws Exception {
        Curl.post(runner.masterNode(), "/" + index + "/_abtest/settings")
            .body(getFileString(json)).execute();
        runner.refresh();
    }

    protected void createDummyIndex(final String index, final int num) {
        final BulkRequest bulkRequest = new BulkRequest();
        for(int i=0;i<num;i++) {
            final IndexRequest indexRequest = new IndexRequest();
            indexRequest.index(index).type("dummy").id(String.valueOf(i)).source("field1", "value1");
            bulkRequest.add(indexRequest);
        }
        runner.client().bulk(bulkRequest).actionGet();
        runner.refresh();
    }

    public String getFileString(final String fileName) throws IOException {
        BufferedReader br = null;
        final StringBuilder sb = new StringBuilder();
        try {
            br =
                new BufferedReader(new InputStreamReader(this.getClass().getClassLoader()
                    .getResourceAsStream(fileName)));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return sb.toString();
    }
}
