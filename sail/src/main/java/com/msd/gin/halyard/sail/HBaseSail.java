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
package com.msd.gin.halyard.sail;

import com.msd.gin.halyard.common.HalyardTableUtils;
import com.msd.gin.halyard.strategy.HalyardEvaluationStrategy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.eclipse.rdf4j.IsolationLevel;
import org.eclipse.rdf4j.IsolationLevels;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration;
import org.eclipse.rdf4j.common.iteration.ExceptionConvertingIteration;
import org.eclipse.rdf4j.common.iteration.TimeLimitIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleNamespace;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.SD;
import org.eclipse.rdf4j.model.vocabulary.VOID;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.QueryRoot;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.Var;
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategy;
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedService;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.RepositoryFederatedService;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.BindingAssigner;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.CompareOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.ConjunctiveConstraintSplitter;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.ConstantOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.DisjunctiveConstraintOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.FilterOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.IterativeEvaluationOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.OrderLimitOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.QueryJoinOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.QueryModelNormalizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.SameTermFilterOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.StrictEvaluationStrategy;
import org.eclipse.rdf4j.query.impl.EmptyBindingSet;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.ntriples.NTriplesUtil;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.UnknownSailTransactionStateException;
import org.eclipse.rdf4j.sail.UpdateContext;

/**
 * HBaseSail is RDF storage implementation on top of Apache HBase.
 * It implements both interfaces - Sail and SailConnection.
 * @author Adam Sotona (MSD)
 */
public final class HBaseSail implements Sail, SailConnection, FederatedServiceResolver {

    /**
     * Ticker is a simple service interface that is notified when some data are processed.
     * It's purpose is to notify caller (for example MapReduce task) that the execution is still alive.
     */
    public interface Ticker {

        /**
         * This method is called whenever a new Statement is populated from HBase.
         */
        public void tick();
    }

    private static final Logger LOG = Logger.getLogger(HBaseSail.class.getName());
    private static final long STATUS_CACHING_TIMEOUT = 60000l;
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final long DEFAULT_THRESHOLD = 1000l;

    private final Configuration config;
    final String tableName;
    final boolean create;
    final boolean pushStrategy;
    final int splitBits;
    final EvaluationStatistics statistics;
    final int evaluationTimeout;
    private boolean readOnly = false;
    private long readOnlyTimestamp = -1;
    private final Ticker ticker;

    HTable table = null;

    private final Map<String, Namespace> namespaces = new HashMap<>();
    private final Map<String, RepositoryFederatedService> federatedServices = new HashMap<>();

    /**
     * Construct HBaseSail object with given arguments.
     * @param config Hadoop Configuration to access HBase
     * @param tableName HBase table name
     * @param create boolean option to create the table if does not exists
     * @param splitBits int number of bits used for calculation of HTable region pre-splits (applies for new tables only)
     * @param pushStrategy boolean option to use {@link com.msd.gin.halyard.strategy.HalyardEvaluationStrategy} instead of {@link org.eclipse.rdf4j.query.algebra.evaluation.impl.StrictEvaluationStrategy}
     * @param evaluationTimeout int timeout in seconds for each query evaluation, negative values mean no timeout
     * @param ticker optional Ticker callback for keep-alive notifications
     */
    public HBaseSail(Configuration config, String tableName, boolean create, int splitBits, boolean pushStrategy, int evaluationTimeout, Ticker ticker) {
        this.config = config;
        this.tableName = tableName;
        this.create = create;
        this.splitBits = splitBits;
        this.pushStrategy = pushStrategy;
        this.statistics = new EvaluationStatistics() {
            @Override
            protected EvaluationStatistics.CardinalityCalculator createCardinalityCalculator() {
                return new CardinalityCalculator() {
                    @Override
                    protected double getCardinality(StatementPattern sp) {
                        Var contextVar = sp.getContextVar();
                        IRI graphNode = contextVar == null || !contextVar.hasValue() ? HALYARD.STATS_ROOT_NODE : (IRI)contextVar.getValue();
                        long triples = getTriplesCount(graphNode, -1l);
                        if (triples > 0) {
                            return triples
                                    * subsetTriplesPart(graphNode, VOID_EXT.SUBJECT, sp.getSubjectVar(), triples)
                                    * subsetTriplesPart(graphNode, VOID.PROPERTY, sp.getPredicateVar(), triples)
                                    * subsetTriplesPart(graphNode, VOID_EXT.OBJECT, sp.getObjectVar(), triples);
                        } else {
                            return super.getCardinality(sp);
                        }
                    }

                    private long getTriplesCount(IRI subjectNode, long defaultValue) {
                        try (CloseableIteration<? extends Statement, SailException> ci = getStatements(subjectNode, VOID.TRIPLES, null, true, HALYARD.STATS_GRAPH_CONTEXT)) {
                            if (ci.hasNext()) {
                                Value v = ci.next().getObject();
                                if (v instanceof Literal) try {
                                    return ((Literal)v).longValue();
                                } catch (NumberFormatException ignore) {}
                                LOG.log(Level.WARNING, "Invalid statistics for:" + subjectNode);
                            }
                        }
                        return defaultValue;
                    }

                    private double subsetTriplesPart(IRI graph, IRI partitionType, Var partitionVar, double total) {
                        if (partitionVar == null || !partitionVar.hasValue()) {
                            return 1.0;
                        } else {
                            return getTriplesCount(SimpleValueFactory.getInstance().createIRI(graph.stringValue() + "_" + partitionType.getLocalName() + "_" + ENC.encodeToString(HalyardTableUtils.hashKey(NTriplesUtil.toNTriplesString(partitionVar.getValue()).getBytes()))), DEFAULT_THRESHOLD) /total;
                        }
                    }
                };
            }
        };
        this.evaluationTimeout = evaluationTimeout;
        this.ticker = ticker;
    }

    @Override
    public void setDataDir(File dataDir) {
    }

    @Override
    public File getDataDir() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize() throws SailException {
        try {
            table = HalyardTableUtils.getTable(config, tableName, create, splitBits);
            try (CloseableIteration<? extends Statement, SailException> nsIter = getStatements(null, HALYARD.NAMESPACE_PREFIX_PROPERTY, null, true)) {
                while (nsIter.hasNext()) {
                    Statement st = nsIter.next();
                    if (st.getObject() instanceof Literal) {
                        String prefix = st.getObject().stringValue();
                        String name = st.getSubject().stringValue();
                        namespaces.put(prefix, new SimpleNamespace(prefix, name));
                    }
                }
            }
        } catch (IOException ex) {
            throw new SailException(ex);
        }
    }

    @Override
    public FederatedService getService(String serviceUrl) throws QueryEvaluationException {
        if (serviceUrl.startsWith(HALYARD.NAMESPACE)) {
            String federatedTable = serviceUrl.substring(HALYARD.NAMESPACE.length());
            RepositoryFederatedService s = federatedServices.get(federatedTable);
            if (s == null) {
                s = new RepositoryFederatedService(new SailRepository(new HBaseSail(config, federatedTable, false, 0, true, evaluationTimeout, ticker)));
                federatedServices.put(federatedTable, s);
                s.initialize();
            }
            return s;
        } else {
            throw new QueryEvaluationException("Unsupported service URL: " + serviceUrl);
        }
    }

    @Override
    public void shutDown() throws SailException {
        try {
            table.close();
            table = null;
        } catch (IOException ex) {
            throw new SailException(ex);
        }
        for (RepositoryFederatedService s : federatedServices.values()) {
            s.shutdown();
        }
    }

    @Override
    public boolean isWritable() throws SailException {
        if (readOnlyTimestamp + STATUS_CACHING_TIMEOUT < System.currentTimeMillis()) try {
            readOnly = table.getTableDescriptor().isReadOnly();
            readOnlyTimestamp = System.currentTimeMillis();
        } catch (IOException ex) {
            throw new SailException(ex);
        }
        return !readOnly;
    }

    @Override
    public SailConnection getConnection() throws SailException {
        return this;
    }

    @Override
    public ValueFactory getValueFactory() {
        return SimpleValueFactory.getInstance();
    }

    @Override
    public List<IsolationLevel> getSupportedIsolationLevels() {
        return Collections.singletonList((IsolationLevel) IsolationLevels.NONE);
    }

    @Override
    public IsolationLevel getDefaultIsolationLevel() {
        return IsolationLevels.NONE;
    }

    @Override
    public boolean isOpen() throws SailException {
        return table != null;
    }

    @Override
    public void close() throws SailException {
    }

    private static Resource[] normalizeContexts(Resource... contexts) {
        if (contexts == null || contexts.length == 0) {
            return new Resource[] {null};
        } else {
            return contexts;
        }
    }

    @Override
    public CloseableIteration<? extends BindingSet, QueryEvaluationException> evaluate(TupleExpr tupleExpr, Dataset dataset, BindingSet bindings, final boolean includeInferred) throws SailException {
        tupleExpr = tupleExpr.clone();
        if (!(tupleExpr instanceof QueryRoot)) {
            // Add a dummy root node to the tuple expressions to allow the
            // optimizers to modify the actual root node
            tupleExpr = new QueryRoot(tupleExpr);
        }
        final long startTime = System.currentTimeMillis();
        TripleSource source = new TripleSource() {
            @Override
            public CloseableIteration<? extends Statement, QueryEvaluationException> getStatements(Resource subj, IRI pred, Value obj, Resource... contexts) throws QueryEvaluationException {
                try {
                    return new ExceptionConvertingIteration<Statement, QueryEvaluationException>(new StatementScanner(startTime, subj, pred, obj, contexts)) {
                        @Override
                        protected QueryEvaluationException convert(Exception e) {
                            return new QueryEvaluationException(e);
                        }

                    };
                } catch (SailException ex) {
                    throw new QueryEvaluationException(ex);
                }
            }

            @Override
            public ValueFactory getValueFactory() {
                return SimpleValueFactory.getInstance();
            }
        };

        EvaluationStrategy strategy = pushStrategy ? new HalyardEvaluationStrategy(source, dataset, this, evaluationTimeout) : new StrictEvaluationStrategy(source, dataset, this);

        new BindingAssigner().optimize(tupleExpr, dataset, bindings);
        new ConstantOptimizer(strategy).optimize(tupleExpr, dataset, bindings);
        new CompareOptimizer().optimize(tupleExpr, dataset, bindings);
        new ConjunctiveConstraintSplitter().optimize(tupleExpr, dataset, bindings);
        new DisjunctiveConstraintOptimizer().optimize(tupleExpr, dataset, bindings);
        new SameTermFilterOptimizer().optimize(tupleExpr, dataset, bindings);
        new QueryModelNormalizer().optimize(tupleExpr, dataset, bindings);
        new QueryJoinOptimizer(statistics).optimize(tupleExpr, dataset, bindings);
        // new SubSelectJoinOptimizer().optimize(tupleExpr, dataset, bindings);
        new IterativeEvaluationOptimizer().optimize(tupleExpr, dataset, bindings);
        new FilterOptimizer().optimize(tupleExpr, dataset, bindings);
        new OrderLimitOptimizer().optimize(tupleExpr, dataset, bindings);

        try {
            CloseableIteration<? extends BindingSet, QueryEvaluationException> iter = strategy.evaluate(tupleExpr, EmptyBindingSet.getInstance());
            return evaluationTimeout <= 0 ? iter : new TimeLimitIteration<BindingSet, QueryEvaluationException>(iter, 1000l * evaluationTimeout) {
                @Override
                protected void throwInterruptedException() throws QueryEvaluationException {
                    throw new QueryEvaluationException("Query evaluation exceeded specified timeout " + evaluationTimeout + "s");
                }
            };
        } catch (QueryEvaluationException ex) {
            throw new SailException(ex);
        }
    }

    @Override
    public CloseableIteration<? extends Resource, SailException> getContextIDs() throws SailException {
        final CloseableIteration<? extends Statement, SailException> scanner = getStatements(HALYARD.STATS_ROOT_NODE, SD.NAMED_GRAPH_PROPERTY, null, true, HALYARD.STATS_GRAPH_CONTEXT);
        return new CloseableIteration<Resource, SailException>() {
            @Override
            public void close() throws SailException {
                scanner.close();
            }

            @Override
            public boolean hasNext() throws SailException {
                return scanner.hasNext();
            }

            @Override
            public Resource next() throws SailException {
                return (IRI)scanner.next().getObject();
            }

            @Override
            public void remove() throws SailException {
                throw new UnsupportedOperationException();
            }

        };
    }

    @Override
    public CloseableIteration<? extends Statement, SailException> getStatements(Resource subj, IRI pred, Value obj, boolean includeInferred, Resource... contexts) throws SailException {
        return new StatementScanner(System.currentTimeMillis(), subj, pred, obj, contexts);
    }

    @Override
    public synchronized long size(Resource... contexts) throws SailException {
        long size = 0;
        if (contexts != null && contexts.length > 0 && contexts[0] != null) {
            for (Resource ctx : contexts) {
                try (CloseableIteration<? extends Statement, SailException> scanner = getStatements(ctx, VOID.TRIPLES, null, true, HALYARD.STATS_GRAPH_CONTEXT)) {
                    if (scanner.hasNext()) {
                        size += ((Literal)scanner.next().getObject()).longValue();
                    }
                    if (scanner.hasNext()) {
                        throw new SailException("Multiple different values");
                    }
                }
            }
        } else {
            try (CloseableIteration<? extends Statement, SailException> scanner = getStatements(HALYARD.STATS_ROOT_NODE, VOID.TRIPLES, null, true, HALYARD.STATS_GRAPH_CONTEXT)) {
                if (scanner.hasNext()) {
                    size += ((Literal)scanner.next().getObject()).longValue();
                }
                if (scanner.hasNext()) {
                    throw new SailException("Multiple different values");
                }
            }
        }
        return size;
    }

    @Override
    public void begin() throws SailException {
    }

    @Override
    public void begin(IsolationLevel level) throws UnknownSailTransactionStateException, SailException {
        if (level != null && level != IsolationLevels.NONE) {
            throw new UnknownSailTransactionStateException("Isolation level " + level + " is not compatible with this HBaseSail");
        }
    }

    @Override
    public void flush() throws SailException {
    }

    @Override
    public void prepare() throws SailException {
    }

    @Override
    public void commit() throws SailException {
        try {
            table.flushCommits();
        } catch (IOException ex) {
            throw new SailException(ex);
        }
    }

    @Override
    public void rollback() throws SailException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isActive() throws UnknownSailTransactionStateException {
        return true;
    }

    @Override
    public void addStatement(UpdateContext op, Resource subj, IRI pred, Value obj, Resource... contexts) throws SailException {
        addStatement(subj, pred, obj, contexts);
    }

    @Override
    public void addStatement(Resource subj, IRI pred, Value obj, Resource... contexts) throws SailException {
        for (Resource ctx : normalizeContexts(contexts)) {
            addStatementInternal(subj, pred, obj, ctx);
        }
    }

    private void addStatementInternal(Resource subj, IRI pred, Value obj, Resource context) throws SailException {
        if (!isWritable()) throw new SailException(tableName + " is read only");
        try {
            for (KeyValue kv : HalyardTableUtils.toKeyValues(subj, pred, obj, context)) {
                table.put(new Put(kv.getRowArray(), kv.getRowOffset(), kv.getRowLength(), kv.getTimestamp()).add(kv));
            }
        } catch (IOException e) {
            throw new SailException(e);
        }
    }

    @Override
    public void removeStatement(UpdateContext op, Resource subj, IRI pred, Value obj, Resource... contexts) throws SailException {
        if (!isWritable()) throw new SailException(tableName + " is read only");
        try {
            List<Delete> deletes = new ArrayList<>();
            for (Resource ctx : normalizeContexts(contexts)) {
                for (KeyValue kv : HalyardTableUtils.toKeyValues(subj, pred, obj, ctx)) {
                    deletes.add(new Delete(kv.getRowArray(), kv.getRowOffset(), kv.getRowLength()).addColumn(kv.getFamily(), kv.getQualifier()));
                }
            }
            table.delete(deletes);
        } catch (IOException e) {
            throw new SailException(e);
        }
    }

    @Override
    public void removeStatements(Resource subj, IRI pred, Value obj, Resource... contexts) throws SailException {
        if (!isWritable()) throw new SailException(tableName + " is read only");
        contexts = normalizeContexts(contexts);
        if (subj == null && pred == null && obj == null && contexts[0] == null) {
            clearAll();
        } else {
            try (CloseableIteration<? extends Statement, SailException> iter = getStatements(subj, pred, obj, true, contexts)) {
                while (iter.hasNext()) {
                    Statement st = iter.next();
                    removeStatement(null, st.getSubject(), st.getPredicate(), st.getObject(), st.getContext());
                }
            }
        }
    }

    @Override
    public void startUpdate(UpdateContext op) throws SailException {
    }

    @Override
    public void endUpdate(UpdateContext op) throws SailException {
    }

    @Override
    public void clear(Resource... contexts) throws SailException {
        removeStatements(null, null, null, contexts);
    }

    private void clearAll() throws SailException {
        if (!isWritable()) throw new SailException(tableName + " is read only");
        try {
            table = HalyardTableUtils.truncateTable(table);
        } catch (IOException ex) {
            throw new SailException(ex);
        }
    }

    @Override
    public String getNamespace(String prefix) throws SailException {
        Namespace namespace = namespaces.get(prefix);
        return (namespace == null) ? null : namespace.getName();
    }

    @Override
    public CloseableIteration<? extends Namespace, SailException> getNamespaces() {
        return new CloseableIteratorIteration<>(namespaces.values().iterator());
    }

    @Override
    public void setNamespace(String prefix, String name) throws SailException {
        Namespace oldNS = namespaces.put(prefix, new SimpleNamespace(prefix, name));
        ValueFactory vf = SimpleValueFactory.getInstance();
        try {
            if (oldNS != null) {
                removeStatement(null, vf.createIRI(oldNS.getName()), HALYARD.NAMESPACE_PREFIX_PROPERTY, vf.createLiteral(prefix));
            }
            addStatementInternal(vf.createIRI(name), HALYARD.NAMESPACE_PREFIX_PROPERTY, vf.createLiteral(prefix), null);
        } catch (SailException e) {
            LOG.log(Level.WARNING, "Namespace prefix could not be presisted due to an exception", e);
        }
    }

    @Override
    public void removeNamespace(String prefix) throws SailException {
        ValueFactory vf = SimpleValueFactory.getInstance();
        Namespace ns = namespaces.remove(prefix);
        if (ns != null) try {
            removeStatement(null, vf.createIRI(ns.getName()), HALYARD.NAMESPACE_PREFIX_PROPERTY, vf.createLiteral(prefix));
        } catch (SailException e) {
            LOG.log(Level.WARNING, "Namespace prefix could not be removed due to an exception", e);
        }
    }

    @Override
    public void clearNamespaces() throws SailException {
        try {
            removeStatements(null, HALYARD.NAMESPACE_PREFIX_PROPERTY, null);
        } catch (SailException e) {
            LOG.log(Level.WARNING, "Namespaces could not be cleared due to an exception", e);
        }
        namespaces.clear();
    }

    private class StatementScanner implements CloseableIteration<Statement, SailException> {

        private final Resource subj;
        private final IRI pred;
        private final Value obj;
        private final Iterator<Resource> contexts;
        private ResultScanner rs = null;
        private final long endTime;
        private Statement next = null;
        private Iterator<Statement> iter = null;

        public StatementScanner(long startTime, Resource subj, IRI pred, Value obj, Resource...contexts) throws SailException {
            this.subj = subj;
            this.pred = pred;
            this.obj = obj;
            this.contexts = Arrays.asList(normalizeContexts(contexts)).iterator();
            this.endTime = startTime + (1000l * evaluationTimeout);
        }

        private Result nextResult() throws IOException {
            while (true) {
                if (rs == null) {
                    if (contexts.hasNext()) {
                        rs = table.getScanner(HalyardTableUtils.scan(subj, pred, obj, contexts.next()));
                    } else {
                        return null;
                    }
                }
                Result res = rs.next();
                if (ticker != null) ticker.tick();
                if (res == null) {
                    rs.close();
                    rs = null;
                } else {
                    return res;
                }
            }
        }

        @Override
        public void close() throws SailException {
            if (rs != null) {
                rs.close();
            }
        }

        @Override
        public synchronized boolean hasNext() throws SailException {
            if (evaluationTimeout > 0 && System.currentTimeMillis() > endTime) {
                throw new SailException("Statements scanning exceeded specified timeout " + evaluationTimeout + "s");
            }
            if (next == null) try {
                while (true) {
                    if (iter == null) {
                        Result res = nextResult();
                        if (res == null) {
                            return false;
                        } else {
                            iter = HalyardTableUtils.parseStatements(res).iterator();
                        }
                    }
                    while (iter.hasNext()) {
                        Statement s = iter.next();
                        if ((subj == null || subj.equals(s.getSubject())) && (pred == null || pred.equals(s.getPredicate())) && (obj == null || obj.equals(s.getObject()))) {
                            next = s;
                            return true;
                        }
                    }
                    iter = null;
                }
            } catch (IOException e) {
                throw new SailException(e);
            } else {
                return true;
            }
        }

        @Override
        public synchronized Statement next() throws SailException {
            if (hasNext()) {
                Statement st = next;
                next = null;
                return st;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() throws SailException {
            throw new UnsupportedOperationException();
        }
    }
}
