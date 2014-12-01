/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.integration.transaction;


import java.util.List;


public class XABridgeJmsJdbcTestCase extends AbstractDerbyTestCase
{
    private static final int NUMBER_OF_MESSAGES = 1;
    
    protected String getConfigResources()
    {
        return "org/mule/test/integration/transaction/xabridge-jms-jdbc-mule.xml";
    }

    // @Override
    protected void emptyTable() throws Exception
    {
        try
        {
            execSqlUpdate("DELETE FROM TEST");
        }
        catch (Exception e)
        {
            execSqlUpdate("CREATE TABLE TEST(ID INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 0) NOT NULL PRIMARY KEY,TYPE INTEGER,DATA VARCHAR(255),ACK TIMESTAMP,RESULT VARCHAR(255))");
        }
    }

    protected void doTestXaBridge(boolean rollback) throws Exception
    {
        XABridgeComponent.mayRollback = rollback;

        List results = execSqlQuery("SELECT * FROM TEST");
        assertEquals(0, results.size());

        for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
        {
            execSqlUpdate("INSERT INTO TEST(TYPE, DATA) VALUES (1, 'Test " + i + "')");
        }
        results = execSqlQuery("SELECT * FROM TEST WHERE TYPE = 1");
        assertEquals(NUMBER_OF_MESSAGES, results.size());

        long t0 = System.currentTimeMillis();
        while (true)
        {
            results = execSqlQuery("SELECT * FROM TEST WHERE TYPE = 2");
            logger.info("Results found: " + results.size());
            if (results.size() >= NUMBER_OF_MESSAGES)
            {
                break;
            }
            assertTrue(System.currentTimeMillis() - t0 < 20000);
            Thread.sleep(500);
        }
        
        assertTrue(results.size() >= NUMBER_OF_MESSAGES);
    }

    public void testXaBridgeWithoutRollbacks() throws Exception
    {
        doTestXaBridge(false);
    }

    public void testXaBridgeWithRollbacks() throws Exception
    {
        doTestXaBridge(true);
    }
}