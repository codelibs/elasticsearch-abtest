package org.codelibs.elasticsearch.abtest;

import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner.newConfigs;

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
    @Test
    public void test_rest() throws Exception {
        Thread.sleep(1000000);
    }
}
