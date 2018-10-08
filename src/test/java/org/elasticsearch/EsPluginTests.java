package org.elasticsearch;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.cluster.routing.UnassignedInfo;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;


public class EsPluginTests extends ESIntegTestCase {

    private static final String INDEX_NAME = "test-index";
    private static final String TYPE_NAME = "test-type";

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(VectorSearchPlugin.class);
    }

    private XContentBuilder exampleSettings() throws IOException {
        return jsonBuilder().startObject()
                .field(SETTING_NUMBER_OF_SHARDS, numberOfShards())
                .field(SETTING_NUMBER_OF_REPLICAS, numberOfReplicas())
                .field(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING.getKey(), 0)
                .endObject();
    }

    private XContentBuilder exampleMapping(int dimensions) throws IOException {
        return jsonBuilder()
            .startObject()
                .startObject("properties")
                    .startObject("v")
                        .field("type", "vector")
                        .field("dimensions", Integer.toString(dimensions))
                    .endObject()
                .endObject()
            .endObject();
    }

    private XContentBuilder exampleDocument(String v) throws IOException {
        return jsonBuilder().startObject().field("v", v).endObject();
    }

    private void doCreateIndex(int dimensions) throws IOException {
        client().admin().indices().prepareCreate(INDEX_NAME).setSettings(exampleSettings()).addMapping(TYPE_NAME, exampleMapping(dimensions)).get();
    }

    @Test
    public void smokeTest() throws IOException {
        doCreateIndex(8);
        ensureGreen(INDEX_NAME);
        indexExists(INDEX_NAME);
    }

    @Test
    public void itWorks() throws IOException {
        doCreateIndex(8);

        String[] vectors = new String[] {
                "-0.75,-0.75,-0.75,-0.75,-0.75,-0.75,-0.75,-0.75",
                "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0",
                "0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5",
                "0.75,0.75,0.75,0.75,0.75,0.75,0.75,0.75",
                "5.0,0.5,0.5,0.5,0.5,0.5,0.5,0.5",
                "0.99,0.99,0.99,0.99,0.99,0.99,0.99,1.01",
                "2.0,2.0,2.0,2.0,2.0,2.0,2.0,2.0"
        };

        for (String v : vectors)
            client().prepareIndex(INDEX_NAME, TYPE_NAME).setSource(exampleDocument(v)).get();

        refresh(INDEX_NAME);

        SearchResponse response = client().prepareSearch(INDEX_NAME).setExplain(true).setQuery(rangeQuery("v").from("-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0").to("1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0")).get();

        assertEquals(4, response.getHits().totalHits);
    }

    @Test(expected = ElasticsearchException.class)
    public void wrongDimensionsFails() throws IOException {
        doCreateIndex(3);

        client().prepareIndex(INDEX_NAME, TYPE_NAME).setSource(exampleDocument("1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0")).get();
    }
}
