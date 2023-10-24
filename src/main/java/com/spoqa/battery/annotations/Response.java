/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Response {

    boolean required() default false;
    String value() default "";

}
