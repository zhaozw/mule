/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.db.internal.domain.connection.mysql;

import org.mule.extension.db.internal.domain.connection.DbConnectionParameters;
import org.mule.extension.db.internal.domain.connection.DbConnectionProvider;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.ParameterGroup;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;

/**
 * Creates connections to a MySQL database.
 *
 * @since 4.0
 */
@DisplayName("MySQL Connection")
@Alias("my-sql")
public class MySqlConnectionProvider extends DbConnectionProvider {

  @ParameterGroup
  private MySqlConnectionParameters parameters;

  @Override
  public DbConnectionParameters getConnectionParameters() {
    return parameters;
  }
}
