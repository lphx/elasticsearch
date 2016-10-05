/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.watcher.actions.pagerduty;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.xpack.common.text.TextTemplateEngine;
import org.elasticsearch.xpack.notification.pagerduty.IncidentEvent;
import org.elasticsearch.xpack.notification.pagerduty.PagerDutyAccount;
import org.elasticsearch.xpack.notification.pagerduty.PagerDutyService;
import org.elasticsearch.xpack.notification.pagerduty.SentEvent;
import org.elasticsearch.xpack.watcher.actions.Action;
import org.elasticsearch.xpack.watcher.actions.ExecutableAction;
import org.elasticsearch.xpack.watcher.execution.WatchExecutionContext;
import org.elasticsearch.xpack.watcher.support.Variables;
import org.elasticsearch.xpack.watcher.watch.Payload;

import java.util.Map;

public class ExecutablePagerDutyAction extends ExecutableAction<PagerDutyAction> {

    private final TextTemplateEngine templateEngine;
    private final PagerDutyService pagerDutyService;

    public ExecutablePagerDutyAction(PagerDutyAction action, Logger logger, PagerDutyService pagerDutyService,
                                     TextTemplateEngine templateEngine) {
        super(action, logger);
        this.pagerDutyService = pagerDutyService;
        this.templateEngine = templateEngine;
    }

    @Override
    public Action.Result execute(final String actionId, WatchExecutionContext ctx, Payload payload) throws Exception {

        PagerDutyAccount account = action.event.account != null ?
                pagerDutyService.getAccount(action.event.account) :
                pagerDutyService.getDefaultAccount();

        if (account == null) {
            // the account associated with this action was deleted
            throw new IllegalStateException("account [" + action.event.account + "] was not found. perhaps it was deleted");
        }

        Map<String, Object> model = Variables.createCtxModel(ctx, payload);
        IncidentEvent event = action.event.render(ctx.watch().id(), actionId, templateEngine, model, account.getDefaults());

        if (ctx.simulateAction(actionId)) {
            return new PagerDutyAction.Result.Simulated(event);
        }

        SentEvent sentEvent = account.send(event, payload);
        return new PagerDutyAction.Result.Executed(account.getName(), sentEvent);
    }

}
