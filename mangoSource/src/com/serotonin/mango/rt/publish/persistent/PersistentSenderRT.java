package com.serotonin.mango.rt.publish.persistent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.mango.Common;
import com.serotonin.mango.DataTypes;
import com.serotonin.mango.db.dao.DataPointDao;
import com.serotonin.mango.db.dao.PointValueDao;
import com.serotonin.mango.rt.dataImage.DataPointRT;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.rt.dataImage.types.ImageValue;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.event.AlarmLevels;
import com.serotonin.mango.rt.event.type.EventType;
import com.serotonin.mango.rt.event.type.PublisherEventType;
import com.serotonin.mango.rt.publish.PublishQueue;
import com.serotonin.mango.rt.publish.PublishQueueEntry;
import com.serotonin.mango.rt.publish.PublishedPointRT;
import com.serotonin.mango.rt.publish.PublisherRT;
import com.serotonin.mango.rt.publish.SendThread;
import com.serotonin.mango.util.DateUtils;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.hierarchy.PointFolder;
import com.serotonin.mango.vo.hierarchy.PointHierarchy;
import com.serotonin.mango.vo.hierarchy.PointHierarchyEventDispatcher;
import com.serotonin.mango.vo.hierarchy.PointHierarchyListener;
import com.serotonin.mango.vo.publish.persistent.PersistentPointVO;
import com.serotonin.mango.vo.publish.persistent.PersistentSenderVO;
import com.serotonin.timer.CronTimerTrigger;
import com.serotonin.timer.TimerTask;
import com.serotonin.timer.TimerTrigger;
import com.serotonin.util.SerializationHelper;
import com.serotonin.util.StringUtils;
import com.serotonin.util.queue.ByteQueue;
import com.serotonin.web.i18n.LocalizableMessage;

public class PersistentSenderRT extends PublisherRT<PersistentPointVO> {
    static final Log LOG = LogFactory.getLog(PersistentSenderRT.class);

    public static final int CONNECTION_FAILED_EVENT = 11;
    public static final int PROTOCOL_FAILURE_EVENT = 12;
    public static final int CONNECTION_ABORTED_EVENT = 13;
    public static final int CONNECTION_LOST_EVENT = 14;
    public static final int SYNC_COMPLETION_EVENT = 15;

    final EventType connectionFailedEventType = new PublisherEventType(getId(), CONNECTION_FAILED_EVENT);
    final EventType protocolFailureEventType = new PublisherEventType(getId(), PROTOCOL_FAILURE_EVENT);
    final EventType connectionAbortedEventType = new PublisherEventType(getId(), CONNECTION_ABORTED_EVENT);
    final EventType connectionLostEventType = new PublisherEventType(getId(), CONNECTION_LOST_EVENT);
    final EventType syncCompletionEventType = new PublisherEventType(getId(), SYNC_COMPLETION_EVENT);

    final PersistentSenderVO vo;
    private PersistentSendThread sendThread;

    public PersistentSenderRT(PersistentSenderVO vo) {
        super(vo);
        this.vo = vo;
    }

    PublishQueue<PersistentPointVO> getPublishQueue() {
        return queue;
    }

    List<PublishedPointRT<PersistentPointVO>> getPointRTs() {
        return pointRTs;
    }

    @Override
    protected void pointInitialized(PublishedPointRT<PersistentPointVO> rt) {
        super.pointInitialized(rt);

        DataPointRT pointRT = Common.ctx.getRuntimeManager().getDataPoint(rt.getVo().getDataPointId());
        if (pointRT != null) {
            updatePublishedPointVO(rt.getVo(), pointRT.getVO());

            // Send the updated point info.
            ByteQueue queue = new ByteQueue();
            queue.pushU2B(rt.getVo().getIndex());
            queue.push(rt.getVo().getSerializedDataPoint());
            Packet packet = Packet.borrowPacket(PacketType.POINT_UPDATE, queue);
            sendThread.sendPacket(packet);
        }
    }

    //
    //
    // Lifecycle
    //
    @Override
    public void initialize() {
        // Cache the data point VOs for use during runtime.
        DataPointDao dataPointDao = new DataPointDao();
        int index = 0;
        for (PersistentPointVO p : vo.getPoints()) {
            DataPointVO dpvo = dataPointDao.getDataPoint(p.getDataPointId());
            p.setIndex(index++);
            updatePublishedPointVO(p, dpvo);
        }

        sendThread = new PersistentSendThread();
        super.initialize(sendThread);
    }

    private void updatePublishedPointVO(PersistentPointVO ppvo, DataPointVO dpvo) {
        ppvo.setXid(dpvo.getXid());
        ppvo.setSerializedDataPoint(SerializationHelper.writeObjectToArray(dpvo));
    }

    class PersistentSendThread extends SendThread {
        Socket socket;
        InputStream in;
        OutputStream out;
        ByteQueue writeBuffer = new ByteQueue();
        int version = 2;
        SyncHandler syncHandler;
        PointHierarchySync pointHierarchySync;
        private final List<Packet> packetsToSend = new CopyOnWriteArrayList<Packet>();
        private long lastTestPacket;

        public PersistentSendThread() {
            super("PersistentSenderRT.SendThread");
        }

        @Override
        public void initialize() {
            super.initialize();

            if (vo.getSyncType() != PersistentSenderVO.SYNC_TYPE_NONE) {
                // Add a schedule to do the sync
                //String pattern = "15 0/5 * * * ?"; // Testing pattern. Every 5 minutes.
                //

                String pattern;
                if (vo.getSyncType() == PersistentSenderVO.SYNC_TYPE_DAILY)
                    pattern = "0 0 1 * * ?";
                else if (vo.getSyncType() == PersistentSenderVO.SYNC_TYPE_WEEKLY)
                    pattern = "0 0 1 ? * MON";
                else if (vo.getSyncType() == PersistentSenderVO.SYNC_TYPE_MONTHLY)
                    pattern = "0 0 1 1 * ?";
                else
                    throw new ShouldNeverHappenException("Invalid sync type: " + vo.getSyncType());

                try {
                    syncHandler = new SyncHandler(new CronTimerTrigger(pattern));
                }
                catch (ParseException e) {
                    throw new ShouldNeverHappenException(e);
                }

                Common.timer.schedule(syncHandler);
            }

            pointHierarchySync = new PointHierarchySync();
            PointHierarchyEventDispatcher.addListener(pointHierarchySync);
        }

        @Override
        public void terminate() {
            PointHierarchyEventDispatcher.removeListener(pointHierarchySync);

            super.terminate();

            if (syncHandler != null) {
                // Cancel the sync handler if it is running.
                if (syncHandler != null)
                    syncHandler.cancel();

                synchronized (syncHandler) {
                    syncHandler.notify();
                }
            }
        }

        @Override
        protected void runImpl() {
            while (isRunning()) {
                if (socket == null) {
                    try {
                        openConnection();
                    }
                    catch (IOException e) {
                        Common.ctx.getEventManager().returnToNormal(connectionAbortedEventType,
                                System.currentTimeMillis());
                        Common.ctx.getEventManager().returnToNormal(protocolFailureEventType,
                                System.currentTimeMillis());
                        raiseConnectionEvent(connectionFailedEventType, e);
                        closeConnection(2000);
                    }
                    catch (PersistentAbortException e) {
                        Common.ctx.getEventManager().returnToNormal(protocolFailureEventType,
                                System.currentTimeMillis());
                        raiseConnectionEvent(connectionAbortedEventType, e);
                        closeConnection(10000);
                    }
                    catch (PersistentProtocolException e) {
                        Common.ctx.getEventManager().returnToNormal(connectionAbortedEventType,
                                System.currentTimeMillis());
                        raiseConnectionEvent(protocolFailureEventType, e);
                        closeConnection(60000);
                    }

                    if (socket == null)
                        continue;

                    Common.ctx.getEventManager().returnToNormal(connectionAbortedEventType, System.currentTimeMillis());
                    Common.ctx.getEventManager().returnToNormal(protocolFailureEventType, System.currentTimeMillis());
                    Common.ctx.getEventManager().returnToNormal(connectionLostEventType, System.currentTimeMillis());

                    writePointHierarchy();
                }

                PublishQueueEntry<PersistentPointVO> entry = getPublishQueue().next();

                if (entry != null) {
                    try {
                        send(entry);
                        getPublishQueue().remove(entry);
                    }
                    catch (IOException e) {
                        raiseConnectionEvent(connectionLostEventType, e);
                        // The send failed. Close the connection and attempt to re-open.
                        closeConnection(0);
                    }
                }
                else if (packetsToSend.size() > 0) {
                    Packet packet = packetsToSend.remove(0);
                    try {
                        Packet.writePacket(out, version, packet);
                    }
                    catch (IOException e) {
                        raiseConnectionEvent(connectionLostEventType, e);
                        // The send failed. Close the connection and attempt to re-open.
                        closeConnection(0);
                    }
                    finally {
                        packet.release();
                    }
                }
                else {
                    try {
                        // Read messages from the server.
                        Packet packet = Packet.readPacketNoBlock(in, version);
                        if (packet != null) {
                            try {
                                // Handle the packet
                                if (packet.getType() == PacketType.RANGE_COUNT) {
                                    if (syncHandler != null)
                                        syncHandler.responseReceived(packet);
                                }
                                else
                                    LOG.error("Unexpected packet type: " + packet.getType());
                            }
                            finally {
                                packet.release();
                            }
                        }
                        else if (lastTestPacket + Packet.TEST_PACKET_SEND_DELAY < System.currentTimeMillis()) {
                            // Fire off a test packet for fun.
                            Packet.writePacket(out, version, PacketType.TEST, Packet.EMPTY);
                            lastTestPacket = System.currentTimeMillis();
                        }
                        else
                            // Take a break.
                            waitImpl(2000);
                    }
                    catch (IOException e) {
                        raiseConnectionEvent(connectionLostEventType, e);
                        closeConnection(0);
                    }
                    catch (PersistentAbortException e) {
                        raiseConnectionEvent(connectionLostEventType, e.getLocalizableMessage());
                        closeConnection(0);
                    }
                    catch (PersistentProtocolException e) {
                        raiseConnectionEvent(connectionLostEventType, e);
                        closeConnection(0);
                    }
                }
            }

            closeConnection(0);
        }

        void sendPacket(Packet packet) {
            packetsToSend.add(packet);
            synchronized (this) {
                notify();
            }
        }

        private void send(PublishQueueEntry<PersistentPointVO> entry) throws IOException {
            //
            // Data
            writeBuffer.pushU2B(entry.getVo().getIndex());
            MangoValue value = entry.getPvt().getValue();
            writeBuffer.push(value.getDataType());
            switch (entry.getPvt().getValue().getDataType()) {
            case DataTypes.BINARY:
                writeBuffer.push(value.getBooleanValue() ? 1 : 0);
                break;
            case DataTypes.MULTISTATE:
                writeBuffer.pushS4B(value.getIntegerValue());
                break;
            case DataTypes.NUMERIC:
                Packet.pushDouble(writeBuffer, value.getDoubleValue());
                break;
            case DataTypes.ALPHANUMERIC:
                Packet.pushString(writeBuffer, value.getStringValue());
                break;
            case DataTypes.IMAGE:
                byte[] data;
                try {
                    data = ((ImageValue) value).getImageData();
                }
                catch (IOException e) {
                    LOG.warn("Error reading image data", e);
                    // Don't propagate the exception since the problem is on this side of the connection.
                    return;
                }
                writeBuffer.pushS4B(((ImageValue) value).getType());
                writeBuffer.pushS4B(data.length);
                writeBuffer.push(data);
                break;
            }

            Packet.pushLong(writeBuffer, entry.getPvt().getTime());

            Packet.writePacket(out, version, PacketType.DATA, writeBuffer);
        }

        private void openConnection() throws IOException, PersistentProtocolException, PersistentAbortException {
            Socket localSocket = new Socket(vo.getHost(), vo.getPort());
            localSocket.setSoTimeout(Packet.PUBLISHER_SOCKET_TIMEOUT);
            Common.ctx.getEventManager().returnToNormal(connectionFailedEventType, System.currentTimeMillis());
            in = localSocket.getInputStream();
            out = localSocket.getOutputStream();

            //
            // Version. Always sent and received in version 1.
            Packet.writePacket(out, 1, PacketType.VERSION, new byte[] { (byte) version });

            Packet packet = Packet.readPacket(in, 1);
            try {
                if (packet.getType() != PacketType.VERSION)
                    throw new PersistentProtocolException("Expected version, got " + packet.getType());
                version = packet.getPayload().popU1B();
            }
            finally {
                packet.release();
            }

            //
            // Authentication key
            Packet.pushString(writeBuffer, vo.getAuthorizationKey());
            Packet.writePacket(out, version, PacketType.AUTH_KEY, writeBuffer);

            packet = Packet.readPacket(in, version);
            try {
                if (packet.getType() != PacketType.AUTH_KEY)
                    throw new PersistentProtocolException("Expected auth key, got " + packet.getType());
                if (packet.getPayload().size() != 0)
                    throw new PersistentProtocolException("Expected empty payload");
            }
            finally {
                packet.release();
            }

            //
            // Points
            String prefix = "";
            if (!StringUtils.isEmpty(vo.getXidPrefix()))
                prefix = vo.getXidPrefix();

            for (PersistentPointVO point : vo.getPoints()) {
                Packet.pushString(writeBuffer, prefix + point.getXid());
                writeBuffer.push(point.getSerializedDataPoint());
                Packet.writePacket(out, version, PacketType.POINT, writeBuffer);
                getPointResponse();
            }

            // Send an empty packet to indicate that we're done.
            Packet.writePacket(out, version, PacketType.POINT, Packet.EMPTY);
            getPointResponse();

            socket = localSocket;
        }

        private void getPointResponse() throws IOException, PersistentAbortException, PersistentProtocolException {
            Packet packet = Packet.readPacket(in, version);
            try {
                if (packet.getType() != PacketType.POINT)
                    throw new PersistentProtocolException("Expected points, got " + packet.getType());
                if (packet.getPayload().size() != 0)
                    throw new PersistentProtocolException("Expected empty payload");
            }
            finally {
                packet.release();
            }
        }

        private void closeConnection(int sleep) {
            if (socket != null) {
                //
                // Close
                try {
                    Packet.writePacket(out, version, PacketType.CLOSE, Packet.EMPTY);
                    socket.close();
                }
                catch (IOException e) {
                    // LOG.warn("", e);
                }
                finally {
                    socket = null;
                    in = null;
                    out = null;
                }
            }

            if (sleep > 0)
                waitImpl(sleep);
        }

        /**
         * The data synchronization thread.
         * 
         * @author Matthew Lohbihler
         */
        class SyncHandler extends TimerTask {
            private static final long serialVersionUID = 1L;

            volatile Thread thread;
            private final PointValueDao pointValueDao = new PointValueDao();
            private int nextRequestId;
            private volatile int responseId = -1;
            private volatile long responseCount;
            private int requestsSent;
            private int recordsSynced;
            private final Set<Integer> targetOvercountPoints = new HashSet<Integer>();
            private int responseErrors;
            private int maxCallDepth;
            private final Map<PersistentPointVO, Long> startTimes = new HashMap<PersistentPointVO, Long>();
            private boolean pointUpdated;

            public SyncHandler(TimerTrigger trigger) {
                super(trigger);
            }

            @SuppressWarnings("synthetic-access")
            @Override
            protected void run(long runtime) {
                if (thread != null) {
                    LOG.warn("A data synchronization run was not started because a previous one is still running");
                    return;
                }

                long start = System.currentTimeMillis();
                thread = Thread.currentThread();
                nextRequestId = 0;
                requestsSent = 0;
                recordsSynced = 0;
                targetOvercountPoints.clear();
                responseErrors = 0;

                try {
                    DateTime cutoff = new DateTime(runtime);
                    // cutoff = DateUtils.truncateDateTime(cutoff, Common.TimePeriods.MINUTES); // TESTING
                    cutoff = DateUtils.truncateDateTime(cutoff, Common.TimePeriods.DAYS);

                    LOG.info("Sync handler running with cutoff: " + cutoff);

                    for (PublishedPointRT<PersistentPointVO> point : getPointRTs()) {
                        if (isCancelled() || socket == null)
                            break;

                        if (!point.isPointEnabled())
                            continue;

                        checkPoint(point.getVo(), cutoff.getMillis());
                    }
                }
                finally {
                    thread = null;
                }

                LocalizableMessage lm = new LocalizableMessage("event.pb.persistent.syncCompleted.details",
                        getPointRTs().size(), requestsSent, recordsSynced, targetOvercountPoints.size(),
                        responseErrors, DateUtils.getDuration(System.currentTimeMillis() - start));
                Common.ctx.getEventManager().raiseEvent(syncCompletionEventType, System.currentTimeMillis(), false,
                        AlarmLevels.NONE, lm, createEventContext());
            }

            void checkPoint(PersistentPointVO point, long to) {
                // Determine the date range that we need to check.
                Long from = startTimes.get(point);
                if (from == null) {
                    from = pointValueDao.getInceptionDate(point.getDataPointId());
                    startTimes.put(point, from);
                }

                if (from == -1)
                    // There are no values for this point yet, so ignore.
                    return;

                if (from > to)
                    // Nothing in the range we're interested in, so ignore.
                    return;

                // Start recursing through the range
                pointUpdated = false;
                checkRangeImpl(point, from, to, 1);
            }

            /**
             * The recursive method that synchronizes range counts on both sides of the connection.
             */
            void checkRangeImpl(PersistentPointVO point, long from, long to, int depth) {
                if (maxCallDepth < depth) {
                    maxCallDepth = depth;
                    LOG.warn("New max call depth of " + maxCallDepth);
                }

                // Send the packet
                responseId = -1;

                // Send the range check request.
                int requestId = nextRequestId++;
                if (nextRequestId > 0xFFFFFF)
                    nextRequestId = 0;

                // Create the request.
                ByteQueue queue = new ByteQueue();
                queue.pushU3B(requestId);
                queue.pushU2B(point.getIndex());
                Packet.pushLong(queue, from);
                Packet.pushLong(queue, to);

                if (socket == null)
                    // If we've lost the connection, give up.
                    return;

                Packet packet = Packet.borrowPacket(PacketType.RANGE_COUNT, queue);
                sendPacket(packet);

                // Check how many records are in that range.
                long count = pointValueDao.dateRangeCount(point.getDataPointId(), from, to);

                // Wait for the response ...
                synchronized (this) {
                    // Just check to see if we should wait.
                    if (isCancelled())
                        return;

                    // ... if we haven't received it already.
                    if (responseId == -1) {
                        try {
                            // Wait up to 20 minutes
                            wait(20 * 60 * 1000);
                        }
                        catch (InterruptedException e) {
                            // no op
                        }
                    }

                    // Check to see if we were canceled.
                    if (isCancelled())
                        return;
                }

                // Check for a bad response id.
                if (responseId != requestId) {
                    if (responseId == -1)
                        // This really shouldn't happen. At least, we'd like to prevent it from happening as much as
                        // possible, so let's make sure we know about it.
                        LOG.error("No response received for request id " + requestId);
                    else
                        LOG.error("Request/response id mismatch: " + requestId);

                    responseErrors++;
                    pointUpdated = true;
                    return;
                }

                requestsSent++;

                if (responseCount == -1) {
                    pointUpdated = true;
                    // The point is unavailable. Just quit.
                    return;
                }

                if (count == responseCount) {
                    // Counts match. Done here.
                    if (!pointUpdated)
                        startTimes.put(point, to + 1);
                    return;
                }

                if (responseCount == 0) {
                    // None of the records in this range exist in the target. So, send them by adding them to the send
                    // queue.
                    List<PointValueTime> pvts = pointValueDao.getPointValuesBetween(point.getDataPointId(), from,
                            to + 1);
                    if (LOG.isInfoEnabled())
                        LOG.info("Syncing records: count=" + count + ", queried=" + pvts.size() + ", point="
                                + point.getXid() + ", from=" + from + ", to=" + to);
                    publish(point, pvts);
                    recordsSynced += pvts.size();
                    pointUpdated = true;
                    return;
                }

                if (count == 0) {
                    if (LOG.isInfoEnabled())
                        LOG.info("Overcount detected: local=" + count + ", target=" + responseCount + ", point="
                                + point.getXid() + ", from=" + from + ", to=" + to);
                    return;
                }

                if (count < responseCount)
                    // This can happen if the source is only logging changes but sending updates, and the target is 
                    // logging all. Reconciliation in this case means checking every record, so this is not a condition 
                    // that is handled. Just noted.
                    targetOvercountPoints.add(point.getDataPointId());

                if (from == to)
                    // An overcount on the target. Most likely duplicate records. Ignore.
                    return;

                // There are differences in this range. Split the range and recurse to find them.
                long mid = ((to - from) >> 1) + from;
                checkRangeImpl(point, from, mid, depth + 1);
                checkRangeImpl(point, mid + 1, to, depth + 1);
            }

            void responseReceived(Packet packet) {
                synchronized (this) {
                    responseId = packet.getPayload().popU3B();
                    responseCount = packet.popLong();
                    // Break the sync thread out of the wait.
                    notify();
                }
            }
        }

        class PointHierarchySync implements PointHierarchyListener {
            @Override
            public void pointHierarchySaved(PointFolder root) {
                PointHierarchy hierarchy = new PointHierarchy(root);
                writePointHierarchy(hierarchy);
            }
        }

        void writePointHierarchy() {
            Common.timer.execute(new Runnable() {
                @Override
                public void run() {
                    writePointHierarchy(new DataPointDao().getPointHierarchy());
                }
            });
        }

        void writePointHierarchy(PointHierarchy hierarchy) {
            for (PersistentPointVO p : vo.getPoints()) {
                List<String> path = hierarchy.getPath(p.getDataPointId());

                ByteQueue queue = new ByteQueue();
                queue.pushU2B(p.getIndex());
                queue.pushU2B(path.size());
                for (String s : path)
                    Packet.pushString(queue, s);

                Packet packet = Packet.borrowPacket(PacketType.POINT_HIERARCHY, queue);
                sendPacket(packet);
            }
        }
    }

    void raiseConnectionEvent(EventType type, Exception e) {
        LocalizableMessage lm;
        if (e instanceof PersistentAbortException)
            lm = ((PersistentAbortException) e).getLocalizableMessage();
        else
            lm = new LocalizableMessage("common.default", e.getMessage());

        raiseConnectionEvent(type, lm);
    }

    void raiseConnectionEvent(EventType type, LocalizableMessage lm) {
        Common.ctx.getEventManager().raiseEvent(type, System.currentTimeMillis(), true, AlarmLevels.URGENT, lm,
                createEventContext());
    }
}
