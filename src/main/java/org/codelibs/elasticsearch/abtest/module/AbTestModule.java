package org.codelibs.elasticsearch.abtest.module;

import org.codelibs.elasticsearch.abtest.service.AbTestService;
import org.elasticsearch.common.inject.AbstractModule;

public class AbTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AbTestService.class).asEagerSingleton();
    }
}