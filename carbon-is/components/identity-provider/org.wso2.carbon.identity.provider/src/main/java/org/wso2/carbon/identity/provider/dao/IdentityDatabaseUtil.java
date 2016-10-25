package org.wso2.carbon.identity.provider.dao;

import java.sql.Connection;

/**
 * Utils class to perform 
 */
public class IdentityDatabaseUtil {
    public static Connection getDBConnection() {
        return (Connection) new DBConnection();
    }
}
