package org.codelibs.elasticsearch.abtest.rest;

import static org.elasticsearch.rest.RestStatus.OK;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jdk.nashorn.internal.parser.JSONParser;
import org.codelibs.elasticsearch.abtest.service.AbTestService;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;

public class AbTestSettingsRestAction extends BaseRestHandler {
    private static final String TEST_SWEETS = "test_sweets";

    protected final AbTestService service;


    @Inject
    public AbTestSettingsRestAction(final Settings settings, final Client client,
                                    final RestController controller, final AbTestService abTestService) {
        super(settings, controller, client);

        controller.registerHandler(RestRequest.Method.POST,
            "/{index}/_abtest/settings", this);
        controller.registerHandler(RestRequest.Method.PUT,
            "/{index}/_abtest/settings", this);
        controller.registerHandler(RestRequest.Method.DELETE,
            "/{index}/_abtest/settings", this);


        this.service = abTestService;
    }

    @Override
    protected void handleRequest(final RestRequest request,
            final RestChannel channel, Client client) {

        try {
            final BytesReference bytesReference = request.content();
            final Map<String, Object> content = JsonXContent.jsonXContent.createParser(bytesReference).mapAndClose();

            switch(request.method()) {
                case POST:
                case PUT:
                    final List<Map<String, Object>> testSweets = (List)content.get(TEST_SWEETS);
                    int testCount = 0;

                    break;

                default:
                    break;
            }





            final XContentBuilder builder = JsonXContent.contentBuilder();
            builder.startObject();
            builder.field("index", request.param("index"));
            builder.field("type", request.param("type"));
            builder.field("description", "This is a elasticsearch-abtest response: "
                    + new Date().toString());
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(OK, builder));
        } catch (final Exception e) {
            try {
                channel.sendResponse(new BytesRestResponse(channel, e));
            } catch (final IOException e1) {
                logger.error("Failed to send a failure response.", e1);
            }
        }
    }

}
