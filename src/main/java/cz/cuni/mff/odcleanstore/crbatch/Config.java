package cz.cuni.mff.odcleanstore.crbatch;

import cz.cuni.mff.odcleanstore.conflictresolution.AggregationSpec;
import cz.cuni.mff.odcleanstore.crbatch.io.EnumOutputFormat;



/**
 * Class encapsulating CR-batch configuration.
 * @author Jan Michelfeit
 * @TODO javadoc
 * @TODO split into part representing the configuration file and part representing general config (e.g. queryTimeout)
 */
public class Config {
    private String databaseConnectionString;
    private String databaseUsername;
    private String databasePassword;
    private Integer queryTimeout = ConfigConstants.DEFAULT_QUERY_TIMEOUT;
    private String namedGraphConstraintPattern;
    private AggregationSpec aggregationSpec;
    private EnumOutputFormat outputFormat;
    
    /**
     * @return the databaseConnectionString
     */
    public String getDatabaseConnectionString() {
        return databaseConnectionString;
    }

    /**
     * @param databaseConnectionString the databaseConnectionString to set
     */
    public void setDatabaseConnectionString(String databaseConnectionString) {
        this.databaseConnectionString = databaseConnectionString;
    }

    /**
     * @return the databaseUsername
     */
    public String getDatabaseUsername() {
        return databaseUsername;
    }

    /**
     * @param databaseUsername the databaseUsername to set
     */
    public void setDatabaseUsername(String databaseUsername) {
        this.databaseUsername = databaseUsername;
    }

    /**
     * @return the databasePassword
     */
    public String getDatabasePassword() {
        return databasePassword;
    }

    /**
     * @param databasePassword the databasePassword to set
     */
    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }
    
    /**
     * 
     * @return
     */
    public Integer getQueryTimeout() {
        return queryTimeout;
    }

    /**
     * 
     * @param queryTimeout
     */
    public void setQueryTimeout(Integer queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    /**
     * @return the agreeCoeficient
     */
    public Double getAgreeCoeficient() {
        return ConfigConstants.AGREE_COEFFICIENT;
    }

    /**
     * @return the scoreIfUnknown
     */
    public Double getScoreIfUnknown() {
        return ConfigConstants.SCORE_IF_UNKNOWN;
    }

    /**
     * @return the namedGraphScoreWeight
     */
    public Double getNamedGraphScoreWeight() {
        return ConfigConstants.NAMED_GRAPH_SCORE_WEIGHT;
    }

    /**
     * @return the publisherScoreWeight
     */
    public Double getPublisherScoreWeight() {
        return ConfigConstants.PUBLISHER_SCORE_WEIGHT;
    }

    /**
     * @return the maxDateDifference
     */
    public Long getMaxDateDifference() {
        return ConfigConstants.MAX_DATE_DIFFERENCE;
    }

    public String getNamedGraphConstraintPattern() {
        return namedGraphConstraintPattern;
    }

    public void setNamedGraphConstraintPattern(String namedGraphConstraintPattern) {
        this.namedGraphConstraintPattern = namedGraphConstraintPattern;
    }

    public String getResultDataURIPrefix() {
        return ConfigConstants.DEFAULT_RESULT_DATA_PREFIX;
    }

    public AggregationSpec getAggregationSpec() {
        return aggregationSpec;
    }

    public void setAggregationSpec(AggregationSpec aggregationSpec) {
        this.aggregationSpec = aggregationSpec;
    }

    public EnumOutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(EnumOutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }
}