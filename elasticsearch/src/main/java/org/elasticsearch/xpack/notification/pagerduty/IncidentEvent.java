/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.notification.pagerduty;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.common.http.HttpMethod;
import org.elasticsearch.xpack.common.http.HttpProxy;
import org.elasticsearch.xpack.common.http.HttpRequest;
import org.elasticsearch.xpack.common.http.Scheme;
import org.elasticsearch.xpack.common.text.TextTemplate;
import org.elasticsearch.xpack.common.text.TextTemplateEngine;
import org.elasticsearch.xpack.watcher.watch.Payload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Official documentation for this can be found at
 *
 * https://developer.pagerduty.com/documentation/howto/manually-trigger-an-incident/
 * https://developer.pagerduty.com/documentation/integration/events/trigger
 * https://developer.pagerduty.com/documentation/integration/events/acknowledge
 * https://developer.pagerduty.com/documentation/integration/events/resolve
 */
public class IncidentEvent implements ToXContent {

    static final String HOST = "events.pagerduty.com";
    static final String PATH = "/generic/2010-04-15/create_event.json";

    final String description;
    @Nullable final HttpProxy proxy;
    @Nullable final String incidentKey;
    @Nullable final String client;
    @Nullable final String clientUrl;
    @Nullable final String account;
    final String eventType;
    final boolean attachPayload;
    @Nullable final IncidentEventContext[] contexts;

    public IncidentEvent(String description, @Nullable String eventType, @Nullable String incidentKey, @Nullable String client,
                         @Nullable String clientUrl, @Nullable String account, boolean attachPayload,
                         @Nullable IncidentEventContext[] contexts, @Nullable HttpProxy proxy) {
        this.description = description;
        if (description == null) {
            throw new IllegalStateException("could not create pagerduty event. missing required [" +
                    Fields.DESCRIPTION.getPreferredName() + "] setting");
        }
        this.incidentKey = incidentKey;
        this.client = client;
        this.clientUrl = clientUrl;
        this.account = account;
        this.proxy = proxy;
        this.attachPayload = attachPayload;
        this.contexts = contexts;
        this.eventType = Strings.hasLength(eventType) ? eventType : "trigger";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IncidentEvent template = (IncidentEvent) o;
        return Objects.equals(description, template.description) &&
                Objects.equals(incidentKey, template.incidentKey) &&
                Objects.equals(client, template.client) &&
                Objects.equals(clientUrl, template.clientUrl) &&
                Objects.equals(attachPayload, template.attachPayload) &&
                Objects.equals(eventType, template.eventType) &&
                Objects.equals(account, template.account) &&
                Objects.equals(proxy, template.proxy) &&
                Arrays.equals(contexts, template.contexts);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(description, incidentKey, client, clientUrl, account, attachPayload, eventType, proxy);
        result = 31 * result + Arrays.hashCode(contexts);
        return result;
    }

    public HttpRequest createRequest(final String serviceKey, final Payload payload) throws IOException {
        return HttpRequest.builder(HOST, -1)
                .method(HttpMethod.POST)
                .scheme(Scheme.HTTPS)
                .path(PATH)
                .proxy(proxy)
                .jsonBody(new ToXContent() {
                    @Override
                    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                        builder.field(Fields.SERVICE_KEY.getPreferredName(), serviceKey);
                        builder.field(Fields.EVENT_TYPE.getPreferredName(), eventType);
                        builder.field(Fields.DESCRIPTION.getPreferredName(), description);
                        if (incidentKey != null) {
                            builder.field(Fields.INCIDENT_KEY.getPreferredName(), incidentKey);
                        }
                        if (client != null) {
                            builder.field(Fields.CLIENT.getPreferredName(), client);
                        }
                        if (clientUrl != null) {
                            builder.field(Fields.CLIENT_URL.getPreferredName(), clientUrl);
                        }
                        if (attachPayload) {
                            builder.startObject(Fields.DETAILS.getPreferredName());
                            builder.field(Fields.PAYLOAD.getPreferredName());
                            payload.toXContent(builder, params);
                            builder.endObject();
                        }
                        if (contexts != null && contexts.length > 0) {
                            builder.startArray(Fields.CONTEXT.getPreferredName());
                            for (IncidentEventContext context : contexts) {
                                context.toXContent(builder, params);
                            }
                            builder.endArray();
                        }
                        return builder;
                    }
                })
                .build();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(Fields.DESCRIPTION.getPreferredName(), description);
        if (incidentKey != null) {
            builder.field(Fields.INCIDENT_KEY.getPreferredName(), incidentKey);
        }
        if (client != null) {
            builder.field(Fields.CLIENT.getPreferredName(), client);
        }
        if (clientUrl != null) {
            builder.field(Fields.CLIENT_URL.getPreferredName(), clientUrl);
        }
        if (account != null) {
            builder.field(Fields.ACCOUNT.getPreferredName(), account);
        }
        if (proxy != null) {
            proxy.toXContent(builder, params);
        }
        builder.field(Fields.ATTACH_PAYLOAD.getPreferredName(), attachPayload);
        if (contexts != null) {
            builder.startArray(Fields.CONTEXT.getPreferredName());
            for (IncidentEventContext context : contexts) {
                context.toXContent(builder, params);
            }
            builder.endArray();
        }
        return builder.endObject();
    }
    public static Template.Builder templateBuilder(String description) {
        return templateBuilder(new TextTemplate(description));
    }

    public static Template.Builder templateBuilder(TextTemplate description) {
        return new Template.Builder(description);
    }

    public static class Template implements ToXContent {

        final TextTemplate description;
        final TextTemplate incidentKey;
        final TextTemplate client;
        final TextTemplate clientUrl;
        final TextTemplate eventType;
        public final String account;
        final Boolean attachPayload;
        final IncidentEventContext.Template[] contexts;
        final HttpProxy proxy;

        public Template(TextTemplate description, TextTemplate eventType, TextTemplate incidentKey, TextTemplate client,
                        TextTemplate clientUrl, String account, Boolean attachPayload, IncidentEventContext.Template[] contexts,
                        HttpProxy proxy) {
            this.description = description;
            this.eventType = eventType;
            this.incidentKey = incidentKey;
            this.client = client;
            this.clientUrl = clientUrl;
            this.account = account;
            this.attachPayload = attachPayload;
            this.contexts = contexts;
            this.proxy = proxy;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Template template = (Template) o;
            return Objects.equals(description, template.description) &&
                   Objects.equals(incidentKey, template.incidentKey) &&
                   Objects.equals(client, template.client) &&
                   Objects.equals(clientUrl, template.clientUrl) &&
                   Objects.equals(eventType, template.eventType) &&
                   Objects.equals(attachPayload, template.attachPayload) &&
                   Objects.equals(account, template.account) &&
                   Objects.equals(proxy, template.proxy) &&
                   Arrays.equals(contexts, template.contexts);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(description, eventType, incidentKey, client, clientUrl, attachPayload, account, proxy);
            result = 31 * result + Arrays.hashCode(contexts);
            return result;
        }

        public IncidentEvent render(String watchId, String actionId, TextTemplateEngine engine, Map<String, Object> model,
                                    IncidentEventDefaults defaults) {
            String description = this.description != null ? engine.render(this.description, model) : defaults.description;
            String incidentKey = this.incidentKey != null ? engine.render(this.incidentKey, model) :
                    defaults.incidentKey != null ? defaults.incidentKey : watchId;
            String client = this.client != null ? engine.render(this.client, model) : defaults.client;
            String clientUrl = this.clientUrl != null ? engine.render(this.clientUrl, model) : defaults.clientUrl;
            String eventType = this.eventType != null ? engine.render(this.eventType, model) : defaults.eventType;
            boolean attachPayload = this.attachPayload != null ? this.attachPayload : defaults.attachPayload;
            IncidentEventContext[] contexts = null;
            if (this.contexts != null) {
                contexts = new IncidentEventContext[this.contexts.length];
                for (int i = 0; i < this.contexts.length; i++) {
                    contexts[i] = this.contexts[i].render(engine, model, defaults);
                }
            }
            return new IncidentEvent(description, eventType, incidentKey, client, clientUrl, account, attachPayload, contexts, proxy);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
            builder.startObject();
            builder.field(Fields.DESCRIPTION.getPreferredName(), description, params);
            if (incidentKey != null) {
                builder.field(Fields.INCIDENT_KEY.getPreferredName(), incidentKey, params);
            }
            if (client != null) {
                builder.field(Fields.CLIENT.getPreferredName(), client, params);
            }
            if (clientUrl != null) {
                builder.field(Fields.CLIENT_URL.getPreferredName(), clientUrl, params);
            }
            if (eventType != null) {
                builder.field(Fields.EVENT_TYPE.getPreferredName(), eventType, params);
            }
            if (attachPayload != null) {
                builder.field(Fields.ATTACH_PAYLOAD.getPreferredName(), attachPayload);
            }
            if (account != null) {
                builder.field(Fields.ACCOUNT.getPreferredName(), account);
            }
            if (proxy != null) {
                proxy.toXContent(builder, params);
            }
            if (contexts != null) {
                builder.startArray(Fields.CONTEXT.getPreferredName());
                for (IncidentEventContext.Template context : contexts) {
                    context.toXContent(builder, params);
                }
                builder.endArray();
            }
            return builder.endObject();
        }

        public static Template parse(String watchId, String actionId, XContentParser parser) throws IOException {
            TextTemplate incidentKey = null;
            TextTemplate description = null;
            TextTemplate client = null;
            TextTemplate clientUrl = null;
            TextTemplate eventType = null;
            String account = null;
            HttpProxy proxy = null;
            Boolean attachPayload = null;
            IncidentEventContext.Template[] contexts = null;

            String currentFieldName = null;
            XContentParser.Token token;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.INCIDENT_KEY)) {
                    try {
                        incidentKey = TextTemplate.parse(parser);
                    } catch (ElasticsearchParseException e) {
                        throw new ElasticsearchParseException("could not parse pager duty event template. failed to parse field [{}]",
                                Fields.INCIDENT_KEY.getPreferredName());
                    }
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.DESCRIPTION)) {
                    try {
                        description = TextTemplate.parse(parser);
                    } catch (ElasticsearchParseException e) {
                        throw new ElasticsearchParseException("could not parse pager duty event template. failed to parse field [{}]",
                                Fields.DESCRIPTION.getPreferredName());
                    }
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.CLIENT)) {
                    try {
                        client = TextTemplate.parse(parser);
                    } catch (ElasticsearchParseException e) {
                        throw new ElasticsearchParseException("could not parse pager duty event template. failed to parse field [{}]",
                                Fields.CLIENT.getPreferredName());
                    }
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.CLIENT_URL)) {
                    try {
                        clientUrl = TextTemplate.parse(parser);
                    } catch (ElasticsearchParseException e) {
                        throw new ElasticsearchParseException("could not parse pager duty event template. failed to parse field [{}]",
                                Fields.CLIENT_URL.getPreferredName());
                    }
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.ACCOUNT)) {
                    try {
                        account = parser.text();
                    } catch (ElasticsearchParseException e) {
                        throw new ElasticsearchParseException("could not parse pager duty event template. failed to parse field [{}]",
                                Fields.CLIENT_URL.getPreferredName());
                    }
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.PROXY)) {
                    proxy = HttpProxy.parse(parser);
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.EVENT_TYPE)) {
                    try {
                        eventType = TextTemplate.parse(parser);
                    } catch (ElasticsearchParseException e) {
                        throw new ElasticsearchParseException("could not parse pager duty event template. failed to parse field [{}]",
                                Fields.EVENT_TYPE.getPreferredName());
                    }
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.ATTACH_PAYLOAD)) {
                    if (token == XContentParser.Token.VALUE_BOOLEAN) {
                        attachPayload = parser.booleanValue();
                    } else {
                        throw new ElasticsearchParseException("could not parse pager duty event template. failed to parse field [{}], " +
                                "expected a boolean value but found [{}] instead", Fields.ATTACH_PAYLOAD.getPreferredName(), token);
                    }
                } else if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.CONTEXT)) {
                    if (token == XContentParser.Token.START_ARRAY) {
                        List<IncidentEventContext.Template> list = new ArrayList<>();
                        while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                            try {
                                list.add(IncidentEventContext.Template.parse(parser));
                            } catch (ElasticsearchParseException e) {
                                throw new ElasticsearchParseException("could not parse pager duty event template. failed to parse field " +
                                        "[{}]", Fields.CONTEXT.getPreferredName());
                            }
                        }
                        contexts = list.toArray(new IncidentEventContext.Template[list.size()]);
                    }
                } else {
                    throw new ElasticsearchParseException("could not parse pager duty event template. unexpected field [{}]",
                            currentFieldName);
                }
            }
            return new Template(description, eventType, incidentKey, client, clientUrl, account, attachPayload, contexts, proxy);
        }

        public static class Builder {

            final TextTemplate description;
            TextTemplate incidentKey;
            TextTemplate client;
            TextTemplate clientUrl;
            TextTemplate eventType;
            String account;
            HttpProxy proxy;
            Boolean attachPayload;
            List<IncidentEventContext.Template> contexts = new ArrayList<>();

            public Builder(TextTemplate description) {
                this.description = description;
            }

            public Builder setIncidentKey(TextTemplate incidentKey) {
                this.incidentKey = incidentKey;
                return this;
            }

            public Builder setClient(TextTemplate client) {
                this.client = client;
                return this;
            }

            public Builder setClientUrl(TextTemplate clientUrl) {
                this.clientUrl = clientUrl;
                return this;
            }

            public Builder setEventType(TextTemplate eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder setAccount(String account) {
                this.account= account;
                return this;
            }

            public Builder setAttachPayload(Boolean attachPayload) {
                this.attachPayload = attachPayload;
                return this;
            }

            public Builder setProxy(HttpProxy proxy) {
                this.proxy = proxy;
                return this;
            }

            public Builder addContext(IncidentEventContext.Template context) {
                this.contexts.add(context);
                return this;
            }

            public Template build() {
                IncidentEventContext.Template[] contexts = this.contexts.isEmpty() ? null :
                        this.contexts.toArray(new IncidentEventContext.Template[this.contexts.size()]);
                return new Template(description, eventType, incidentKey, client, clientUrl, account, attachPayload, contexts, proxy);
            }
        }
    }

    interface Fields {

        ParseField TYPE = new ParseField("type");
        ParseField EVENT_TYPE = new ParseField("event_type");

        ParseField ACCOUNT = new ParseField("account");
        ParseField PROXY = new ParseField("proxy");
        ParseField DESCRIPTION = new ParseField("description");
        ParseField INCIDENT_KEY = new ParseField("incident_key");
        ParseField CLIENT = new ParseField("client");
        ParseField CLIENT_URL = new ParseField("client_url");
        ParseField ATTACH_PAYLOAD = new ParseField("attach_payload");
        ParseField CONTEXT = new ParseField("context");

        ParseField SERVICE_KEY = new ParseField("service_key");
        ParseField PAYLOAD = new ParseField("payload");
        ParseField DETAILS = new ParseField("details");
    }
}
