package com.github.opengrabeso.stravalas;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE) //on class level
@interface Handle {

    enum Method {Get, Put, Post, Delete}

    Method method() default Method.Get;

    String value();
}
