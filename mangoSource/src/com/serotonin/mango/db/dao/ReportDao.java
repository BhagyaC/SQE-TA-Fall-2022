/*
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.springframework.jdbc.core.RowCallbackHandler;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.db.spring.GenericRowMapper;
import com.serotonin.mango.Common;
import com.serotonin.mango.DataTypes;
import com.serotonin.mango.db.DatabaseAccess;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.rt.dataImage.types.AlphanumericValue;
import com.serotonin.mango.rt.dataImage.types.BinaryValue;
import com.serotonin.mango.rt.dataImage.types.ImageValue;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.dataImage.types.MultistateValue;
import com.serotonin.mango.rt.dataImage.types.NumericValue;
import com.serotonin.mango.rt.event.EventInstance;
import com.serotonin.mango.rt.event.type.EventType;
import com.serotonin.mango.view.text.TextRenderer;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.UserComment;
import com.serotonin.mango.vo.report.ReportDataStreamHandler;
import com.serotonin.mango.vo.report.ReportDataValue;
import com.serotonin.mango.vo.report.ReportInstance;
import com.serotonin.mango.vo.report.ReportPointInfo;
import com.serotonin.mango.vo.report.ReportUserComment;
import com.serotonin.mango.vo.report.ReportVO;
import com.serotonin.util.SerializationHelper;
import com.serotonin.util.StringUtils;
import com.serotonin.web.i18n.I18NUtils;
import com.serotonin.web.taglib.Functions;

/**
 * @author Matthew Lohbihler
 */
public class ReportDao extends BaseDao {
    //
    //
    // Report Templates
    //
    private static final String REPORT_SELECT = "select data, id, userId, name from reports ";

    public List<ReportVO> getReports() {
        return query(REPORT_SELECT, new ReportRowMapper());
    }

    public List<ReportVO> getReports(int userId) {
        return query(REPORT_SELECT + "where userId=? order by name", new Object[] { userId }, new ReportRowMapper());
    }

    public ReportVO getReport(int id) {
        return queryForObject(REPORT_SELECT + "where id=?", new Object[] { id }, new ReportRowMapper(), null);
    }

    class ReportRowMapper implements GenericRowMapper<ReportVO> {
        public ReportVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            int i = 0;
            ReportVO report = (ReportVO) SerializationHelper.readObject(rs.getBlob(++i).getBinaryStream());
            report.setId(rs.getInt(++i));
            report.setUserId(rs.getInt(++i));
            report.setName(rs.getString(++i));
            return report;
        }
    }

    public void saveReport(ReportVO report) {
        if (report.getId() == Common.NEW_ID)
            insertReport(report);
        else
            updateReport(report);
    }

    private static final String REPORT_INSERT = "insert into reports (userId, name, data) values (?,?,?)";

    private void insertReport(final ReportVO report) {
        report.setId(doInsert(REPORT_INSERT,
                new Object[] { report.getUserId(), report.getName(), SerializationHelper.writeObject(report) },
                new int[] { Types.INTEGER, Types.VARCHAR, Types.BLOB }));
    }

    private static final String REPORT_UPDATE = "update reports set userId=?, name=?, data=? where id=?";

    private void updateReport(final ReportVO report) {
        ejt.update(
                REPORT_UPDATE,
                new Object[] { report.getUserId(), report.getName(), SerializationHelper.writeObject(report),
                        report.getId() }, new int[] { Types.INTEGER, Types.VARCHAR, Types.BLOB, Types.INTEGER });
    }

    public void deleteReport(int reportId) {
        ejt.update("delete from reports where id=?", new Object[] { reportId });
    }

    //
    //
    // Report Instances
    //
    private static final String REPORT_INSTANCE_SELECT = "select id, userId, name, includeEvents, includeUserComments, reportStartTime, reportEndTime, runStartTime, "
            + "  runEndTime, recordCount, preventPurge " + "from reportInstances ";

    public List<ReportInstance> getReportInstances(int userId) {
        return query(REPORT_INSTANCE_SELECT + "where userId=? order by runStartTime desc", new Object[] { userId },
                new ReportInstanceRowMapper());
    }

    public ReportInstance getReportInstance(int id) {
        return queryForObject(REPORT_INSTANCE_SELECT + "where id=?", new Object[] { id },
                new ReportInstanceRowMapper(), null);
    }

    class ReportInstanceRowMapper implements GenericRowMapper<ReportInstance> {
        public ReportInstance mapRow(ResultSet rs, int rowNum) throws SQLException {
            int i = 0;
            ReportInstance ri = new ReportInstance();
            ri.setId(rs.getInt(++i));
            ri.setUserId(rs.getInt(++i));
            ri.setName(rs.getString(++i));
            ri.setIncludeEvents(rs.getInt(++i));
            ri.setIncludeUserComments(charToBool(rs.getString(++i)));
            ri.setReportStartTime(rs.getLong(++i));
            ri.setReportEndTime(rs.getLong(++i));
            ri.setRunStartTime(rs.getLong(++i));
            ri.setRunEndTime(rs.getLong(++i));
            ri.setRecordCount(rs.getInt(++i));
            ri.setPreventPurge(charToBool(rs.getString(++i)));
            return ri;
        }
    }

    public void deleteReportInstance(int id, int userId) {
        ejt.update("delete from reportInstances where id=? and userId=?", new Object[] { id, userId });
    }

    public int purgeReportsBefore(final long time) {
        return ejt.update("delete from reportInstances where runStartTime<? and preventPurge=?", new Object[] { time,
                boolToChar(false) });
    }

    public void setReportInstancePreventPurge(int id, boolean preventPurge, int userId) {
        ejt.update("update reportInstances set preventPurge=? where id=? and userId=?", new Object[] {
                boolToChar(preventPurge), id, userId });
    }

    /**
     * This method should only be called by the ReportWorkItem.
     */
    private static final String REPORT_INSTANCE_INSERT = "insert into reportInstances "
            + "  (userId, name, includeEvents, includeUserComments, reportStartTime, reportEndTime, runStartTime, "
            + "     runEndTime, recordCount, preventPurge) " + "  values (?,?,?,?,?,?,?,?,?,?)";
    private static final String REPORT_INSTANCE_UPDATE = "update reportInstances set reportStartTime=?, reportEndTime=?, runStartTime=?, runEndTime=?, recordCount=? "
            + "where id=?";

    public void saveReportInstance(ReportInstance instance) {
        if (instance.getId() == Common.NEW_ID)
            instance.setId(doInsert(
                    REPORT_INSTANCE_INSERT,
                    new Object[] { instance.getUserId(), instance.getName(), instance.getIncludeEvents(),
                            boolToChar(instance.isIncludeUserComments()), instance.getReportStartTime(),
                            instance.getReportEndTime(), instance.getRunStartTime(), instance.getRunEndTime(),
                            instance.getRecordCount(), boolToChar(instance.isPreventPurge()) }));
        else
            ejt.update(
                    REPORT_INSTANCE_UPDATE,
                    new Object[] { instance.getReportStartTime(), instance.getReportEndTime(),
                            instance.getRunStartTime(), instance.getRunEndTime(), instance.getRecordCount(),
                            instance.getId() });
    }

    /**
     * This method should only be called by the ReportWorkItem.
     */
    private static final String REPORT_INSTANCE_POINTS_INSERT = "insert into reportInstancePoints " //
            + "(reportInstanceId, dataSourceName, pointName, dataType, startValue, textRenderer, colour, consolidatedChart) "
            + "values (?,?,?,?,?,?,?,?)";

    public static class PointInfo {
        private final DataPointVO point;
        private final String colour;
        private final boolean consolidatedChart;

        public PointInfo(DataPointVO point, String colour, boolean consolidatedChart) {
            this.point = point;
            this.colour = colour;
            this.consolidatedChart = consolidatedChart;
        }

        public DataPointVO getPoint() {
            return point;
        }

        public String getColour() {
            return colour;
        }

        public boolean isConsolidatedChart() {
            return consolidatedChart;
        }
    }

    public int runReport(final ReportInstance instance, List<PointInfo> points, ResourceBundle bundle) {
        PointValueDao pointValueDao = new PointValueDao();
        int count = 0;
        String userLabel = I18NUtils.getMessage(bundle, "common.user");
        String setPointLabel = I18NUtils.getMessage(bundle, "annotation.eventHandler");
        String anonymousLabel = I18NUtils.getMessage(bundle, "annotation.anonymous");
        String deletedLabel = I18NUtils.getMessage(bundle, "common.deleted");

        // The timestamp selection code is used multiple times for different tables
        String timestampSql;
        Object[] timestampParams;
        if (instance.isFromInception() && instance.isToNow()) {
            timestampSql = "";
            timestampParams = new Object[0];
        }
        else if (instance.isFromInception()) {
            timestampSql = "and ${field}<?";
            timestampParams = new Object[] { instance.getReportEndTime() };
        }
        else if (instance.isToNow()) {
            timestampSql = "and ${field}>=?";
            timestampParams = new Object[] { instance.getReportStartTime() };
        }
        else {
            timestampSql = "and ${field}>=? and ${field}<?";
            timestampParams = new Object[] { instance.getReportStartTime(), instance.getReportEndTime() };
        }

        // For each point.
        for (PointInfo pointInfo : points) {
            DataPointVO point = pointInfo.getPoint();
            int dataType = point.getPointLocator().getDataTypeId();

            MangoValue startValue = null;
            if (!instance.isFromInception()) {
                // Get the value just before the start of the report
                PointValueTime pvt = pointValueDao.getPointValueBefore(point.getId(), instance.getReportStartTime());
                if (pvt != null)
                    startValue = pvt.getValue();

                // Make sure the data types match
                if (DataTypes.getDataType(startValue) != dataType)
                    startValue = null;
            }

            // Insert the reportInstancePoints record
            String name = Functions.truncate(point.getName(), 100);

            int reportPointId = doInsert(
                    REPORT_INSTANCE_POINTS_INSERT,
                    new Object[] { instance.getId(), point.getDeviceName(), name, dataType,
                            DataTypes.valueToString(startValue),
                            SerializationHelper.writeObject(point.getTextRenderer()), pointInfo.getColour(),
                            boolToChar(pointInfo.isConsolidatedChart()) }, new int[] { Types.INTEGER, Types.VARCHAR,
                            Types.VARCHAR, Types.INTEGER, Types.VARCHAR, Types.BLOB, Types.VARCHAR, Types.CHAR });

            // Insert the reportInstanceData records
            String insertSQL = "insert into reportInstanceData " + "  select id, " + reportPointId
                    + ", pointValue, ts from pointValues " + "    where dataPointId=? and dataType=? "
                    + StringUtils.replaceMacro(timestampSql, "field", "ts");
            count += ejt.update(insertSQL, appendParameters(timestampParams, point.getId(), dataType));

            String annoCase;
            if (Common.ctx.getDatabaseAccess().getType() == DatabaseAccess.DatabaseType.DERBY)
                annoCase = "    case when pva.sourceType=1 then '" + userLabel //
                        + ": ' || (case when u.username is null then '" + deletedLabel + "' else u.username end) " //
                        + "         when pva.sourceType=2 then '" + setPointLabel + "' " //
                        + "         when pva.sourceType=3 then '" + anonymousLabel + "' " //
                        + "         else 'Unknown source type: ' || cast(pva.sourceType as char(3)) " //
                        + "    end ";
            else if (Common.ctx.getDatabaseAccess().getType() == DatabaseAccess.DatabaseType.MSSQL)
                annoCase = "    case pva.sourceType" //
                        + "        when 1 then '" + userLabel + ": ' + isnull(u.username, '" + deletedLabel + "') " //
                        + "        when 2 then '" + setPointLabel + "'" //
                        + "        when 3 then '" + anonymousLabel + "'" //
                        + "        else 'Unknown source type: ' + cast(pva.sourceType as nvarchar)" //
                        + "    end ";
            else if (Common.ctx.getDatabaseAccess().getType() == DatabaseAccess.DatabaseType.MYSQL)
                annoCase = "    case pva.sourceType" //
                        + "      when 1 then concat('" + userLabel + ": ',ifnull(u.username,'" + deletedLabel + "')) " //
                        + "      when 2 then '" + setPointLabel + "'" //
                        + "      when 3 then '" + anonymousLabel + "'" //
                        + "      else concat('Unknown source type: ', pva.sourceType)" //
                        + "    end ";
            else
                throw new ShouldNeverHappenException("unhandled database type: "
                        + Common.ctx.getDatabaseAccess().getType());

            // Insert the reportInstanceDataAnnotations records
            ejt.update("insert into reportInstanceDataAnnotations " //
                    + "  (pointValueId, reportInstancePointId, textPointValueShort, textPointValueLong, sourceValue) " //
                    + "  select rd.pointValueId, rd.reportInstancePointId, pva.textPointValueShort, " //
                    + "    pva.textPointValueLong, " + annoCase + "  from reportInstanceData rd " //
                    + "    join reportInstancePoints rp on rd.reportInstancePointId = rp.id " //
                    + "    join pointValueAnnotations pva on rd.pointValueId = pva.pointValueId " //
                    + "    left join users u on pva.sourceType=1 and pva.sourceId = u.id " //
                    + "  where rp.id = ?", new Object[] { reportPointId });

            // Insert the reportInstanceEvents records for the point.
            if (instance.getIncludeEvents() != ReportVO.EVENTS_NONE) {
                String eventSQL = "insert into reportInstanceEvents " //
                        + "  (eventId, reportInstanceId, typeId, typeRef1, typeRef2, activeTs, rtnApplicable, rtnTs," //
                        + "   rtnCause, alarmLevel, message, ackTs, ackUsername, alternateAckSource)" //
                        + "  select e.id, " + instance.getId() + ", e.typeId, e.typeRef1, e.typeRef2, e.activeTs, " //
                        + "    e.rtnApplicable, e.rtnTs, e.rtnCause, e.alarmLevel, e.message, e.ackTs, u.username, " //
                        + "    e.alternateAckSource " //
                        + "  from events e join userEvents ue on ue.eventId=e.id " //
                        + "    left join users u on e.ackUserId=u.id " //
                        + "  where ue.userId=? " //
                        + "    and e.typeId=" //
                        + EventType.EventSources.DATA_POINT //
                        + "    and e.typeRef1=? ";

                if (instance.getIncludeEvents() == ReportVO.EVENTS_ALARMS)
                    eventSQL += "and e.alarmLevel > 0 ";

                eventSQL += StringUtils.replaceMacro(timestampSql, "field", "e.activeTs");
                ejt.update(eventSQL, appendParameters(timestampParams, instance.getUserId(), point.getId()));
            }

            // Insert the reportInstanceUserComments records for the point.
            if (instance.isIncludeUserComments()) {
                String commentSQL = "insert into reportInstanceUserComments " //
                        + "  (reportInstanceId, username, commentType, typeKey, ts, commentText)" //
                        + "  select " + instance.getId() + ", u.username, " + UserComment.TYPE_POINT + ", " //
                        + reportPointId + ", uc.ts, uc.commentText " //
                        + "  from userComments uc " //
                        + "    left join users u on uc.userId=u.id " //
                        + "  where uc.commentType=" + UserComment.TYPE_POINT //
                        + "    and uc.typeKey=? ";

                // Only include comments made in the duration of the report.
                commentSQL += StringUtils.replaceMacro(timestampSql, "field", "uc.ts");
                ejt.update(commentSQL, appendParameters(timestampParams, point.getId()));
            }
        }

        // Insert the reportInstanceUserComments records for the selected events
        if (instance.isIncludeUserComments()) {
            String commentSQL = "insert into reportInstanceUserComments " //
                    + "  (reportInstanceId, username, commentType, typeKey, ts, commentText)" //
                    + "  select " + instance.getId() + ", u.username, " + UserComment.TYPE_EVENT + ", uc.typeKey, " //
                    + "    uc.ts, uc.commentText " //
                    + "  from userComments uc " //
                    + "    left join users u on uc.userId=u.id " //
                    + "    join reportInstanceEvents re on re.eventId=uc.typeKey " //
                    + "  where uc.commentType=" + UserComment.TYPE_EVENT //
                    + "    and re.reportInstanceId=? ";
            ejt.update(commentSQL, new Object[] { instance.getId() });
        }

        // If the report had undefined start or end times, update them with values from the data.
        if (instance.isFromInception() || instance.isToNow()) {
            ejt.query(
                    "select min(rd.ts), max(rd.ts) " //
                            + "from reportInstancePoints rp "
                            + "  join reportInstanceData rd on rp.id=rd.reportInstancePointId "
                            + "where rp.reportInstanceId=?", new Object[] { instance.getId() },
                    new RowCallbackHandler() {
                        public void processRow(ResultSet rs) throws SQLException {
                            if (instance.isFromInception())
                                instance.setReportStartTime(rs.getLong(1));
                            if (instance.isToNow())
                                instance.setReportEndTime(rs.getLong(2));
                        }
                    });
        }

        return count;
    }

    private Object[] appendParameters(Object[] toAppend, Object... params) {
        if (toAppend.length == 0)
            return params;
        if (params.length == 0)
            return toAppend;

        Object[] result = new Object[params.length + toAppend.length];
        System.arraycopy(params, 0, result, 0, params.length);
        System.arraycopy(toAppend, 0, result, params.length, toAppend.length);
        return result;
    }

    /**
     * This method guarantees that the data is provided to the setData handler method grouped by point (points are not
     * ordered), and sorted by time ascending.
     */
    private static final String REPORT_INSTANCE_POINT_SELECT = "select id, dataSourceName, pointName, dataType, " // 
            + "startValue, textRenderer, colour, consolidatedChart from reportInstancePoints ";
    private static final String REPORT_INSTANCE_DATA_SELECT = "select rd.pointValue, rda.textPointValueShort, " //
            + "  rda.textPointValueLong, rd.ts, rda.sourceValue "
            + "from reportInstanceData rd "
            + "  left join reportInstanceDataAnnotations rda on "
            + "      rd.pointValueId=rda.pointValueId and rd.reportInstancePointId=rda.reportInstancePointId ";

    public void reportInstanceData(int instanceId, final ReportDataStreamHandler handler) {
        // Retrieve point information.
        List<ReportPointInfo> pointInfos = query(REPORT_INSTANCE_POINT_SELECT + "where reportInstanceId=?",
                new Object[] { instanceId }, new GenericRowMapper<ReportPointInfo>() {
                    public ReportPointInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
                        ReportPointInfo rp = new ReportPointInfo();
                        rp.setReportPointId(rs.getInt(1));
                        rp.setDeviceName(rs.getString(2));
                        rp.setPointName(rs.getString(3));
                        rp.setDataType(rs.getInt(4));
                        String startValue = rs.getString(5);
                        if (startValue != null)
                            rp.setStartValue(MangoValue.stringToValue(startValue, rp.getDataType()));
                        rp.setTextRenderer((TextRenderer) SerializationHelper.readObject(rs.getBlob(6)
                                .getBinaryStream()));
                        rp.setColour(rs.getString(7));
                        rp.setConsolidatedChart(charToBool(rs.getString(8)));
                        return rp;
                    }
                });

        final ReportDataValue rdv = new ReportDataValue();
        for (final ReportPointInfo point : pointInfos) {
            handler.startPoint(point);

            rdv.setReportPointId(point.getReportPointId());
            final int dataType = point.getDataType();
            ejt.query(REPORT_INSTANCE_DATA_SELECT + "where rd.reportInstancePointId=? order by rd.ts",
                    new Object[] { point.getReportPointId() }, new RowCallbackHandler() {
                        public void processRow(ResultSet rs) throws SQLException {
                            switch (dataType) {
                            case (DataTypes.NUMERIC):
                                rdv.setValue(new NumericValue(rs.getDouble(1)));
                                break;
                            case (DataTypes.BINARY):
                                rdv.setValue(new BinaryValue(rs.getDouble(1) == 1));
                                break;
                            case (DataTypes.MULTISTATE):
                                rdv.setValue(new MultistateValue(rs.getInt(1)));
                                break;
                            case (DataTypes.ALPHANUMERIC):
                                rdv.setValue(new AlphanumericValue(rs.getString(2)));
                                if (rs.wasNull())
                                    rdv.setValue(new AlphanumericValue(rs.getString(3)));
                                break;
                            case (DataTypes.IMAGE):
                                rdv.setValue(new ImageValue(Integer.parseInt(rs.getString(2)), rs.getInt(1)));
                                break;
                            default:
                                rdv.setValue(null);
                            }

                            rdv.setTime(rs.getLong(4));
                            rdv.setAnnotation(rs.getString(5));

                            handler.pointData(rdv);
                        }
                    });
        }
        handler.done();
    }

    private static final String EVENT_SELECT = //
    "select eventId, typeId, typeRef1, typeRef2, activeTs, rtnApplicable, rtnTs, rtnCause, alarmLevel, message, " //
            + "ackTs, 0, ackUsername, alternateAckSource " //
            + "from reportInstanceEvents " //
            + "where reportInstanceId=? " //
            + "order by activeTs";
    private static final String EVENT_COMMENT_SELECT = "select username, typeKey, ts, commentText " //
            + "from reportInstanceUserComments " //
            + "where reportInstanceId=? and commentType=? " //
            + "order by ts";

    public List<EventInstance> getReportInstanceEvents(int instanceId) {
        // Get the events.
        final List<EventInstance> events = query(EVENT_SELECT, new Object[] { instanceId },
                new EventDao.EventInstanceRowMapper());
        // Add in the comments.
        ejt.query(EVENT_COMMENT_SELECT, new Object[] { instanceId, UserComment.TYPE_EVENT }, new RowCallbackHandler() {
            public void processRow(ResultSet rs) throws SQLException {
                // Create the comment
                UserComment c = new UserComment();
                c.setUsername(rs.getString(1));
                c.setTs(rs.getLong(3));
                c.setComment(rs.getString(4));

                // Find the event and add the comment
                int eventId = rs.getInt(2);
                for (EventInstance event : events) {
                    if (event.getId() == eventId) {
                        if (event.getEventComments() == null)
                            event.setEventComments(new ArrayList<UserComment>());
                        event.addEventComment(c);
                    }
                }
            }
        });
        // Done
        return events;
    }

    private static final String USER_COMMENT_SELECT = "select rc.username, rc.commentType, rc.typeKey, rp.pointName, " //
            + "  rc.ts, rc.commentText "
            + "from reportInstanceUserComments rc "
            + "  left join reportInstancePoints rp on rc.typeKey=rp.id and rc.commentType="
            + UserComment.TYPE_POINT
            + " " + "where rc.reportInstanceId=? " + "order by rc.ts ";

    public List<ReportUserComment> getReportInstanceUserComments(int instanceId) {
        return query(USER_COMMENT_SELECT, new Object[] { instanceId }, new ReportCommentRowMapper());
    }

    class ReportCommentRowMapper implements GenericRowMapper<ReportUserComment> {
        @Override
        public ReportUserComment mapRow(ResultSet rs, int rowNum) throws SQLException {
            ReportUserComment c = new ReportUserComment();
            c.setUsername(rs.getString(1));
            c.setCommentType(rs.getInt(2));
            c.setTypeKey(rs.getInt(3));
            c.setPointName(rs.getString(4));
            c.setTs(rs.getLong(5));
            c.setComment(rs.getString(6));
            return c;
        }
    }
}
