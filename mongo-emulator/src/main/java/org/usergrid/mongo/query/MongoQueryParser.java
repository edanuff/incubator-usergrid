/*******************************************************************************
 * Copyright 2012 Apigee Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.usergrid.mongo.query;

import static org.apache.commons.collections.MapUtils.getIntValue;

import java.util.Stack;

import org.antlr.runtime.ClassicToken;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;
import org.usergrid.persistence.Query;
import org.usergrid.persistence.Query.SortDirection;
import org.usergrid.persistence.query.tree.AndOperand;
import org.usergrid.persistence.query.tree.Equal;
import org.usergrid.persistence.query.tree.GreaterThan;
import org.usergrid.persistence.query.tree.GreaterThanEqual;
import org.usergrid.persistence.query.tree.LessThan;
import org.usergrid.persistence.query.tree.LessThanEqual;
import org.usergrid.persistence.query.tree.Operand;
import org.usergrid.persistence.query.tree.OrOperand;

/**
 * Parser class to parse mongo queries into usergrid EM queries
 * @author tnine
 *
 */
public class MongoQueryParser {

    /**
     * Convert the bson object query to a native usergrid query
     * @param query
     * @param numberToReturn
     * @return The query
     */
    public static Query toNativeQuery(BSONObject query, int numberToReturn) {
        
        if (query == null) {
            return null;
        }

        BasicBSONObject query_expression = null;
        BasicBSONObject sort_order = null;

        Object o = query.get("$query");
        if (!(o instanceof BasicBSONObject)) {
            o = query.get("query");
        }
        if (o instanceof BasicBSONObject) {
            query_expression = (BasicBSONObject) o;
        }

        o = query.get("$orderby");
        if (!(o instanceof BasicBSONObject)) {
            o = query.get("orderby");
        }
        if (o instanceof BasicBSONObject) {
            sort_order = (BasicBSONObject) o;
        }

        if ((query_expression == null) && (query instanceof BasicBSONObject)) {
            query_expression = (BasicBSONObject) query;
            query_expression.removeField("$orderby");
            query_expression.removeField("$max");
            query_expression.removeField("$min");
        }

        if ((query_expression == null) && (sort_order == null)) {
            return null;
        }

        if (query_expression.size() == 0 && sort_order != null) {
            if (sort_order.size() == 0) {
                return null;
            }
            if ((sort_order.size() == 1) && sort_order.containsField("_id")) {
                return null;
            }
        }

        Query q = new Query();
        
        if (numberToReturn > 0) {
            q.setLimit(numberToReturn);
        }

        if (query_expression != null) {
            Operand root = eval(query_expression);
            q.setRootOperand(root);
        }

        if (sort_order != null) {
            for (String sort : sort_order.keySet()) {
                if (!"_id".equals(sort)) {
                    int s = getIntValue(sort_order.toMap(), "_id", 1);
                    q.addSort(sort, s >= 0 ? SortDirection.ASCENDING
                            : SortDirection.DESCENDING);
                }
            }
        }

        return q;
    }

    /**
     * Evaluate an expression part
     * @param exp
     * @return
     */
    private static Operand eval(BSONObject exp) {
        Operand current = null;
        Object fieldValue = null;

        for (String field : exp.keySet()) {
            fieldValue = exp.get(field);

            if (field.startsWith("$")) {
                // same as OR with multiple values

                // same as OR with multiple values
                if ("$or".equals(field)) {
                    BasicBSONList values = (BasicBSONList) fieldValue;

                    int size = values.size();

                    Stack<Operand> expressions = new Stack<Operand>();

                    for (int i = 0; i < size; i++) {
                        expressions.push(eval((BSONObject) values.get(i)));
                    }

                    // we need to build a tree of expressions
                    while (expressions.size() > 1) {
                        OrOperand or = new OrOperand();
                        or.addChild(expressions.pop());
                        or.addChild(expressions.pop());
                        expressions.push(or);
                    }

                    current = expressions.pop();

                }

                else if ("$and".equals(field)) {

                    BasicBSONList values = (BasicBSONList) fieldValue;

                    int size = values.size();

                    Stack<Operand> expressions = new Stack<Operand>();

                    for (int i = 0; i < size; i++) {
                        expressions.push(eval((BSONObject) values.get(i)));
                    }

                    while (expressions.size() > 1) {
                        AndOperand and = new AndOperand();
                        and.addChild(expressions.pop());
                        and.addChild(expressions.pop());
                        expressions.push(and);
                    }

                    current = expressions.pop();
                }

            }
            // we have a nested object
            else if (fieldValue instanceof BSONObject) {
                current = handleOperand(field, (BSONObject) fieldValue);
            }

            else if (!field.equals("_id")) {
                Equal equality = new Equal(new ClassicToken(0, "="));
                equality.setProperty(field);
                equality.setLiteral(exp.get(field));

                current = equality;
            }
        }
        return current;
    }
    

    /**
     * Handle an operand
     * @param sourceField
     * @param exp
     * @return
     */
    private static Operand handleOperand(String sourceField, BSONObject exp) {

        Operand current = null;
        Object value = null;

        for (String field : exp.keySet()) {
            if (field.startsWith("$")) {
                if ("$gt".equals(field)) {
                    value = exp.get(field);

                    GreaterThan gt = new GreaterThan();
                    gt.setProperty(sourceField);
                    gt.setLiteral(value);

                    current = gt;
                } else if ("$gte".equals(field)) {
                    value = exp.get(field);

                    GreaterThanEqual gte = new GreaterThanEqual();
                    gte.setProperty(sourceField);
                    gte.setLiteral(exp.get(field));

                    current = gte;
                    // http://www.mongodb.org/display/DOCS/Advanced+Queries#AdvancedQueries-%3C%2C%3C%3D%2C%3E%2C%3E%3D
                    // greater than equals
                    // { "field" : { $gte: value } }
                } else if ("$lt".equals(field)) {
                    value = exp.get(field);

                    LessThan lt = new LessThan();
                    lt.setProperty(sourceField);
                    lt.setLiteral(value);

                    current = lt;
                } else if ("$lte".equals(field)) {
                    value = exp.get(field);

                    LessThanEqual lte = new LessThanEqual();
                    lte.setProperty(sourceField);
                    lte.setLiteral(value);

                    current = lte;
                } else if ("$in".equals(field)) {
                    value = exp.get(field);

                    BasicBSONList values = (BasicBSONList) value;

                    int size = values.size();

                    Stack<Operand> expressions = new Stack<Operand>();

                    for (int i = 0; i < size; i++) {
                        Equal equal = new Equal();
                        equal.setProperty(sourceField);
                        equal.setLiteral(values.get(i));

                        expressions.push(equal);
                    }

                    // we need to build a tree of expressions
                    while (expressions.size() > 1) {
                        OrOperand or = new OrOperand();
                        or.addChild(expressions.pop());
                        or.addChild(expressions.pop());
                        expressions.push(or);
                    }

                    current = expressions.pop();

                }

            }
        }

        return current;
    }
    
}
