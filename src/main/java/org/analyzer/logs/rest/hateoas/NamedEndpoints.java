package org.analyzer.logs.rest.hateoas;

import java.lang.annotation.*;


@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NamedEndpoints {

    NamedEndpoint[] value();
}
