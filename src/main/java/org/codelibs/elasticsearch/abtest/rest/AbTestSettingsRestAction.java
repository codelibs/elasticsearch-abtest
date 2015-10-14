package org.codelibs.elasticsearch.abtest.rest;

import static org.elasticsearch.rest.RestStatus.OK;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.codelibs.elasticsearch.abtest.exception.AbTestException;
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
    private static final String TEST_CASES = "testcases";

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
        controller.registerHandler(RestRequest.Method.GET,
            "/{index}/_abtest/settings", this);

        this.service = abTestService;
    }

    @Override
    protected void handleRequest(final RestRequest request,
            final RestChannel channel, Client client) {

        try {
            final String testSweetname = request.param("index");
            final BytesReference bytesReference = request.content();
            final Map<String, Object> content = JsonXContent.jsonXContent.createParser(bytesReference).mapAndClose();

            switch(request.method()) {
                case GET:
                    service.getTestSweet(testSweetname,
                        testCases -> {
                            try {
                                final XContentBuilder builder = JsonXContent.contentBuilder();
                                builder.startObject();
                                builder.field("testsweet", testSweetname);
                                builder.startArray("testcases");
                                for (final AbTestService.TestCase testCase : testCases) {
                                    builder.startObject();
                                    builder.field(AbTestService.TestCase.FIELD_TEST_NAME, testCase.testName);
                                    builder.field(AbTestService.TestCase.FIELD_TEST_INDEX, testCase.testIndexName);
                                    builder.field(AbTestService.TestCase.FIELD_PERCENTAGE, testCase.percentage);
                                    builder.endObject();
                                }
                                builder.endArray();
                                builder.endObject();
                                channel.sendResponse(new BytesRestResponse(OK, builder));
                            } catch(IOException e) {
                                sendErrorResponse(channel, e);
                            }
                        },
                        t -> sendErrorResponse(channel, t));
                    break;
                case POST:
                case PUT:
                    final Object testCasesObj = content.get(TEST_CASES);
                    if(testCasesObj != null) {
                        @SuppressWarnings("unchecked")
                        final List<Map<String, Object>> testCases = (List) testCasesObj;
                        service.updateTestSweet(testSweetname, testCases,
                            acknowledge -> sendAcknowledgeResponse(channel, testSweetname, acknowledge),
                            t -> sendErrorResponse(channel, t));
                    } else {
                        throw new AbTestException("Test case was null.");
                    }
                    break;
                case DELETE:
                    service.deleteTestSweet(testSweetname,
                        acknowledge -> sendAcknowledgeResponse(channel, testSweetname, acknowledge),
                        t -> sendErrorResponse(channel, t)
                    );
                    break;
                default:
                    throw new AbTestException("Method invalid.");
            }
        } catch (final Exception e) {
            sendErrorResponse(channel, e);
        }
    }

    protected void sendAcknowledgeResponse(final RestChannel channel, final String testSweetName, final boolean acknowledge) {
        try {
            final XContentBuilder builder = JsonXContent.contentBuilder();
            builder.startObject();
            builder.field("test_sweet", testSweetName);
            builder.field("acknowledge", acknowledge);
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(OK, builder));
        } catch (IOException e) {
            sendErrorResponse(channel, e);
        }
    }

    protected void sendErrorResponse(final RestChannel channel, final Throwable t) {
        try {
            logger.error(t.getMessage(), t);
            channel.sendResponse(new BytesRestResponse(channel, t));
        } catch(IOException e) {
            logger.error("Failed to send a failure response.", e);
        }
    }

}
