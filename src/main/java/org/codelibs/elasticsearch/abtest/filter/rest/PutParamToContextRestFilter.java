package org.codelibs.elasticsearch.abtest.filter.rest;

import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestFilter;
import org.elasticsearch.rest.RestFilterChain;
import org.elasticsearch.rest.RestRequest;

public class PutParamToContextRestFilter extends RestFilter {

    public PutParamToContextRestFilter() {
        super();
    }

    @Override
    public void process(final RestRequest request, final RestChannel channel, final RestFilterChain restFilterChain) throws Exception {
        request.params().entrySet().iterator()
            .forEachRemaining(entry -> request.putInContext(entry.getKey(), entry.getValue()));
        restFilterChain.continueProcessing(request, channel);
    }
}
