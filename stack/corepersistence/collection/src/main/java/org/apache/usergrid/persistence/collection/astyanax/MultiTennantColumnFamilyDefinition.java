/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.usergrid.persistence.collection.astyanax;


import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.netflix.astyanax.model.ColumnFamily;


/**
 * Bean wrapper for column family information
 *
 * @author tnine
 */
public class MultiTennantColumnFamilyDefinition {

    public static final String COMPARATOR_TYPE = "comparator_type";
    public static final String READ_REPAIR_CHANCE = "read_repair_chance";
    public static final String KEY_VALIDATION = "key_validation_class";
    public static final String VALUE_VALIDATION = "default_validation_class";


    private final ColumnFamily columnFamily;
    private final String comparator;
    private final String keyValidationType;
    private final String valueValidationType;


    public MultiTennantColumnFamilyDefinition( final ColumnFamily columnFamily, final String comparator,
                                               final String keyValidationType, final String valueValidationType ) {

        Preconditions.checkNotNull( columnFamily, "columnFamily is required" );
        Preconditions.checkNotNull( comparator, "comparator is required" );
        Preconditions.checkNotNull( keyValidationType, "keyValidationType is required" );
        Preconditions.checkNotNull( valueValidationType, "valueValidationType is required" );

        this.columnFamily = columnFamily;
        this.comparator = comparator;
        this.keyValidationType = keyValidationType;
        this.valueValidationType = valueValidationType;
    }


    public Map<String, Object> getOptions() {

        Map<String, Object> options = new HashMap<String, Object>();
        options.put( COMPARATOR_TYPE, comparator );
        options.put( KEY_VALIDATION, keyValidationType );
        options.put( VALUE_VALIDATION, valueValidationType );

        //always use 10% load repair chance!
        options.put( READ_REPAIR_CHANCE, 0.1d );

        return options;
    }


    public ColumnFamily getColumnFamily() {
        return columnFamily;
    }
}