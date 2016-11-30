/*
 * Copyright 2016 Merck Sharp & Dohme Corp. a subsidiary of Merck & Co.,
 * Inc., Kenilworth, NJ, USA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.msd.gin.halyard.strategy;

import junit.framework.TestCase;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Adam Sotona (MSD)
 */
public class HalyardStrategyExtendedTest extends TestCase {

    private Repository repo;
    private RepositoryConnection con;

    @Before
    @Override
    public void setUp() throws Exception {
        repo = new SailRepository(new MemoryStoreWithHalyardStrategy());
        repo.initialize();
        con = repo.getConnection();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        con.close();
        repo.shutDown();
    }

    @Test
    public void testIntersect() throws Exception {
        ValueFactory vf = con.getValueFactory();
        con.add(vf.createIRI("http://whatever/subj1"), vf.createIRI("http://whatever/pred1"), vf.createIRI("http://whatever/val1"));
        con.add(vf.createIRI("http://whatever/subj1"), vf.createIRI("http://whatever/pred2"), vf.createIRI("http://whatever/val1"));
        con.add(vf.createIRI("http://whatever/subj2"), vf.createIRI("http://whatever/pred1"), vf.createIRI("http://whatever/val1"));
        con.add(vf.createIRI("http://whatever/subj2"), vf.createIRI("http://whatever/pred2"), vf.createIRI("http://whatever/val2"));
        String serql = "SELECT val FROM {subj} <http://whatever/pred1> {val} INTERSECT SELECT val FROM {subj} <http://whatever/pred2> {val}";
        TupleQueryResult res = con.prepareTupleQuery(QueryLanguage.SERQL, serql).evaluate();
        assertTrue(res.hasNext());
        assertEquals(vf.createIRI("http://whatever/val1"), res.next().getBinding("val").getValue());
        if (res.hasNext()) {
            assertEquals(vf.createIRI("http://whatever/val1"), res.next().getBinding("val").getValue());
        }
    }

    @Test
    public void testLikeAndNamespace() throws Exception {
        ValueFactory vf = con.getValueFactory();
        con.add(vf.createIRI("urn:test/subjx"), vf.createIRI("urn:test/pred"), vf.createLiteral("valuex"));
        con.add(vf.createIRI("urn:test/xsubj"), vf.createIRI("urn:test/pred"), vf.createLiteral("xvalue"));
        String serql = "SELECT * FROM {s} test:pred {o} WHERE s LIKE \"urn:test/subj*\" USING NAMESPACE test = <urn:test/>";
        TupleQueryResult res = con.prepareTupleQuery(QueryLanguage.SERQL, serql).evaluate();
        assertTrue(res.hasNext());
        res.next();
        assertFalse(res.hasNext());
        serql = "SELECT * FROM {s} test:pred {o} WHERE o LIKE \"value*\" USING NAMESPACE test = <urn:test/>";
        res = con.prepareTupleQuery(QueryLanguage.SERQL, serql).evaluate();
        assertTrue(res.hasNext());
        res.next();
        assertFalse(res.hasNext());
    }

    @Test
    public void testSubqueries() throws Exception {
        ValueFactory vf = con.getValueFactory();
        con.add(vf.createIRI("http://whatever/a"), vf.createIRI("http://whatever/val"), vf.createLiteral(1));
        con.add(vf.createIRI("http://whatever/b"), vf.createIRI("http://whatever/val"), vf.createLiteral(2));
        con.add(vf.createIRI("http://whatever/b"), vf.createIRI("http://whatever/attrib"), vf.createLiteral("muhehe"));
        String serql = "SELECT higher FROM {node} <http://whatever/val> {higher} WHERE higher > ANY (SELECT value FROM {} <http://whatever/val> {value})";
        TupleQueryResult res = con.prepareTupleQuery(QueryLanguage.SERQL, serql).evaluate();
        assertTrue(res.hasNext());
        assertEquals(2, ((Literal) res.next().getValue("higher")).intValue());
        serql = "SELECT lower FROM {node} <http://whatever/val> {lower} WHERE lower < ANY (SELECT value FROM {} <http://whatever/val> {value})";
        res = con.prepareTupleQuery(QueryLanguage.SERQL, serql).evaluate();
        assertTrue(res.hasNext());
        assertEquals(1, ((Literal) res.next().getValue("lower")).intValue());
        con.add(vf.createBNode("http://whatever/c"), vf.createIRI("http://whatever/val"), vf.createLiteral(3));
        serql = "SELECT highest FROM {node} <http://whatever/val> {highest} WHERE highest >= ALL (SELECT value FROM {} <http://whatever/val> {value})";
        res = con.prepareTupleQuery(QueryLanguage.SERQL, serql).evaluate();
        assertTrue(res.hasNext());
        assertEquals(3, ((Literal) res.next().getValue("highest")).intValue());
        serql = "SELECT val FROM {node} <http://whatever/val> {val} WHERE val IN (SELECT value FROM {} <http://whatever/val> {value}; <http://whatever/attrib> {\"muhehe\"})";
        res = con.prepareTupleQuery(QueryLanguage.SERQL, serql).evaluate();
        assertTrue(res.hasNext());
        assertEquals(2, ((Literal) res.next().getValue("val")).intValue());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testService() throws Exception {
        String sparql = "SELECT * WHERE {?s ?p ?o . SERVICE <http://whatever/> { ?s ?p ?o . }}";
        con.prepareTupleQuery(QueryLanguage.SPARQL, sparql).evaluate();
    }
}