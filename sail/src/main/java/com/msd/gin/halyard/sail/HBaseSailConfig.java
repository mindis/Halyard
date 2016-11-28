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

import java.util.Optional;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.repository.config.RepositoryConfigSchema;
import org.eclipse.rdf4j.repository.sail.config.SailRepositorySchema;
import org.eclipse.rdf4j.sail.config.AbstractSailImplConfig;
import org.eclipse.rdf4j.sail.config.SailConfigException;
import org.eclipse.rdf4j.sail.config.SailConfigSchema;

/**
 *
 * @author Adam Sotona (MSD)
 */
public final class HBaseSailConfig extends AbstractSailImplConfig {

    /**
     * HBaseSailConfig NAMESPACE IRI
     */
    public static final String NAMESPACE = "http://gin.msd.com/halyard/sail/hbase#";

    final static IRI TABLESPACE, SPLITBITS, CREATE, PUSH, TIMEOUT;

    static {
        ValueFactory factory = SimpleValueFactory.getInstance();
        TABLESPACE = factory.createIRI(NAMESPACE, "tablespace");
        SPLITBITS = factory.createIRI(NAMESPACE, "splitbits");
        CREATE = factory.createIRI(NAMESPACE, "create");
        PUSH = factory.createIRI(NAMESPACE, "pushstrategy");
        TIMEOUT = factory.createIRI(NAMESPACE, "evaluationtimeout");
    }

    private String tablespace = null;
    private int splitBits = 0;
    private boolean create = true;
    private boolean push = true;
    private int evaluationTimeout = 180; //3 min

    /**
     * Sets HBase table name
     * @param tablespace String HBase table name
     */
    public void setTablespace(String tablespace) {
        this.tablespace = tablespace;
    }

    /**
     * Gets HBase table name
     * @return String table name
     */
    public String getTablespace() {
        return tablespace;
    }

    /**
     * Sets number of bits used for HBase table region pre-split
     * @param splitBits int number of bits used for HBase table region pre-split
     */
    public void setSplitBits(int splitBits) {
        this.splitBits = splitBits;
    }

    /**
     * Gets number of bits used for HBase table region pre-split
     * @return int number of bits used for HBase table region pre-split
     */
    public int getSplitBits() {
        return splitBits;
    }

    /**
     * Gets flag if the HBase table should be created
     * @return boolean flag if the HBase table should be created
     */
    public boolean isCreate() {
        return create;
    }

    /**
     * Sets flag if the HBase table should be created
     * @param create boolean flag if the HBase table should be created
     */
    public void setCreate(boolean create) {
        this.create = create;
    }

    /**
     * Gets flag to use {@link com.msd.gin.halyard.strategy.HalyardEvaluationStrategy} instead of {@link org.eclipse.rdf4j.query.algebra.evaluation.impl.StrictEvaluationStrategy}
     * @return boolean flag to use HalyardEvaluationStrategy instead of StrictEvaluationStrategy
     */
    public boolean isPush() {
        return push;
    }

    /**
     * Sets flag to use {@link com.msd.gin.halyard.strategy.HalyardEvaluationStrategy} instead of {@link org.eclipse.rdf4j.query.algebra.evaluation.impl.StrictEvaluationStrategy}
     * @param push boolean flag to use HalyardEvaluationStrategy instead of StrictEvaluationStrategy
     */
    public void setPush(boolean push) {
        this.push = push;
    }

    /**
     * Gets timeout in seconds for each query evaluation, negative values mean no timeout
     * @return int timeout in seconds for each query evaluation, negative values mean no timeout
     */
    public int getEvaluationTimeout() {
        return evaluationTimeout;
    }

    /**
     * Sets timeout in seconds for each query evaluation, negative values mean no timeout
     * @param evaluationTimeout int timeout in seconds for each query evaluation, negative values mean no timeout
     */
    public void setEvaluationTimeout(int evaluationTimeout) {
        this.evaluationTimeout = evaluationTimeout;
    }

    /**
     * Default constructor of HBaseSailConfig
     */
    public HBaseSailConfig() {
        super(HBaseSailFactory.SAIL_TYPE);
    }

    /**
     * Stores configuration into the given Model
     * @param graph Model to store configuration into
     * @return Resource node with the configuration within the Model
     */
    @Override
    public Resource export(Model graph) {
        Resource implNode = super.export(graph);
        ValueFactory vf = SimpleValueFactory.getInstance();
        if (tablespace != null) graph.add(implNode, TABLESPACE, vf.createLiteral(tablespace));
        graph.add(implNode, SPLITBITS, vf.createLiteral(splitBits));
        graph.add(implNode, CREATE, vf.createLiteral(create));
        graph.add(implNode, PUSH, vf.createLiteral(push));
        graph.add(implNode, TIMEOUT, vf.createLiteral(evaluationTimeout));
        return implNode;
    }

    /**
     * Retrieves configuration from the given Model
     * @param graph Model to retrieve the configuration from
     * @param implNode Resource node with the configuration within the Model
     * @throws SailConfigException throws SailConfigException in case of parsing or retrieval problems
     */
    @Override
    public void parse(Model graph, Resource implNode) throws SailConfigException {
        super.parse(graph, implNode);
        Optional<Literal> tablespaceValue = Models.objectLiteral(graph.filter(implNode, TABLESPACE, null));
        if (tablespaceValue.isPresent() && tablespaceValue.get().stringValue().length() > 0) {
            setTablespace(tablespaceValue.get().stringValue());
        } else {
            Optional<Resource> delegate = Models.subject(graph.filter(null, SailConfigSchema.DELEGATE, implNode));
            Optional<Resource> sailImpl = Models.subject(graph.filter(null, SailRepositorySchema.SAILIMPL, delegate.isPresent() ? delegate.get(): implNode));
            if (sailImpl.isPresent()) {
                Optional<Resource> repoImpl = Models.subject(graph.filter(null, RepositoryConfigSchema.REPOSITORYIMPL, sailImpl.get()));
                if (repoImpl.isPresent()) {
                    Optional<Literal> idValue = Models.objectLiteral(graph.filter(repoImpl.get(), RepositoryConfigSchema.REPOSITORYID, null));
                    if (idValue.isPresent()) {
                        setTablespace(idValue.get().stringValue());
                    }
                }
            }

        }
        Optional<Literal> splitBitsValue = Models.objectLiteral(graph.filter(implNode, SPLITBITS, null));
        if (splitBitsValue.isPresent()) try {
            setSplitBits(splitBitsValue.get().intValue());
        } catch (NumberFormatException e) {
            throw new SailConfigException(e);
        }
        Optional<Literal> createValue = Models.objectLiteral(graph.filter(implNode, CREATE, null));
        if (createValue.isPresent()) try {
            setCreate(createValue.get().booleanValue());
        } catch (IllegalArgumentException e) {
            throw new SailConfigException(e);
        }
        Optional<Literal> pushValue = Models.objectLiteral(graph.filter(implNode, PUSH, null));
        if (pushValue.isPresent()) try {
            setPush(pushValue.get().booleanValue());
        } catch (IllegalArgumentException e) {
            throw new SailConfigException(e);
        }
        Optional<Literal> timeoutValue = Models.objectLiteral(graph.filter(implNode, TIMEOUT, null));
        if (timeoutValue.isPresent()) try {
            setEvaluationTimeout(timeoutValue.get().intValue());
        } catch (NumberFormatException e) {
            throw new SailConfigException(e);
        }
    }
}
