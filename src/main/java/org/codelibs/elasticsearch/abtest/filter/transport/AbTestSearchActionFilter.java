package org.codelibs.elasticsearch.abtest.filter.transport;

import org.codelibs.elasticsearch.abtest.service.AbTestService;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.common.base.Strings;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

public class AbTestSearchActionFilter extends AbstractComponent implements ActionFilter {
    private static final String PARAM_RT = "ab_rt";
    private static final String PARAM_HASH_RT = "hash_rt";
    private static final String PARAM_TESTSWEET = "testsweet";
    private static final String HEADER_INVOKED_KEY = "AbTestSearchActionFilter.invoked";

    protected AbTestService service = null;

    @Inject
    public AbTestSearchActionFilter(final Settings settings) {
        super(settings);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public void apply(final String action, final ActionRequest request, final ActionListener listener, final ActionFilterChain chain) {
        if(!SearchAction.INSTANCE.name().equals(action)) {
            chain.proceed(action, request, listener);
            return;
        }

        final SearchRequest searchRequest = (SearchRequest) request;

        if(searchRequest.getHeader(HEADER_INVOKED_KEY) != null) {
            chain.proceed(action, request, listener);
            return;
        }
        searchRequest.putHeader(HEADER_INVOKED_KEY, true);

        final String rt = searchRequest.getFromContext(PARAM_RT);
        if(Strings.isNullOrEmpty(rt)) {
            chain.proceed(action, request, listener);
            return;
        }

        final String hash_rt = searchRequest.getFromContext(PARAM_HASH_RT);
        final boolean doHash = hash_rt == null || Boolean.parseBoolean(hash_rt);
        final String testCaseKey;
        if(doHash) {
            testCaseKey = service.convertTestCaseKey(rt);
        } else {
            testCaseKey = rt;
        }

        String testSweetName = searchRequest.getFromContext(PARAM_TESTSWEET);
        if(Strings.isNullOrEmpty(testSweetName)) {
            if (searchRequest.indices().length != 1) {
                //TODO?
                chain.proceed(action, request, listener);
                return;
            }
            testSweetName = searchRequest.indices()[0];
        }

        service.getTestCaseIndex(testSweetName, testCaseKey,
            rewritedIndex -> {
                searchRequest.indices(rewritedIndex);
                chain.proceed(action, searchRequest, listener);
            });
    }

    @Override
    public void apply(String action, ActionResponse response, ActionListener listener, ActionFilterChain chain) {
        chain.proceed(action, response, listener);
    }

    public void injectService(final AbTestService service) {
        this.service = service;
    }

}
