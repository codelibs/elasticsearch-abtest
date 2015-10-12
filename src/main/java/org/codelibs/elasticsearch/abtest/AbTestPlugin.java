package org.codelibs.elasticsearch.abtest;

import java.util.Collection;

import org.codelibs.elasticsearch.abtest.filter.transport.AbTestSearchActionFilter;
import org.codelibs.elasticsearch.abtest.module.AbTestModule;
import org.codelibs.elasticsearch.abtest.rest.AbTestSettingsRestAction;
import org.codelibs.elasticsearch.abtest.service.AbTestService;
import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.rest.RestModule;

public class AbTestPlugin extends AbstractPlugin {
    @Override
    public String name() {
        return "AbTestPlugin";
    }

    @Override
    public String description() {
        return "This is a elasticsearch-abtest plugin.";
    }

    // for Rest API
    public void onModule(final RestModule module) {
        module.addRestAction(AbTestSettingsRestAction.class);
    }

    public void onModule(final ActionModule module) {
        module.registerFilter(AbTestSearchActionFilter.class);
    }

    // for Service
    @Override
    public Collection<Class<? extends Module>> modules() {
        final Collection<Class<? extends Module>> modules = Lists
                .newArrayList();
        modules.add(AbTestModule.class);
        return modules;
    }

    // for Service
    @SuppressWarnings("rawtypes")
    @Override
    public Collection<Class<? extends LifecycleComponent>> services() {
        final Collection<Class<? extends LifecycleComponent>> services = Lists
                .newArrayList();
        services.add(AbTestService.class);
        return services;
    }
}
