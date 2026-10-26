/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.extension;

import io.github.vijaykumarkothapalli.idp.internal.config.SmartIdpConfiguration;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import org.mule.runtime.api.meta.Category;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;
import org.mule.sdk.api.annotation.Configurations;
import org.mule.sdk.api.annotation.JavaVersionSupport;

import static org.mule.sdk.api.meta.JavaVersion.JAVA_11;
import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;
import static org.mule.sdk.api.meta.JavaVersion.JAVA_8;

/**
 * Smart IDP Connector for MuleSoft Intelligent Document Processing.
 *
 * @author Vijay Kumar Kothapalli
 */
@Extension(name = "Smart IDP", vendor = "Vijay Kumar Kothapalli", category = Category.COMMUNITY)
@Xml(prefix = "smart-idp")
@ErrorTypes(SmartIdpError.class)
@Configurations(SmartIdpConfiguration.class)
@JavaVersionSupport({JAVA_8, JAVA_11, JAVA_17})
public class SmartIdpConnector {
}
