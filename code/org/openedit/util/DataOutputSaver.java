package org.openedit.util;

import org.entermediadb.manager.BaseMediaObject;
import org.entermediadb.scripts.LogListener;
import org.openedit.MultiValued;

public class DataOutputSaver extends BaseMediaObject implements LogListener
{
    protected static final long LOG_SAVE_INTERVAL_MS = 3000L;
    protected String fieldSaveFieldName;
    protected MultiValued inData;

    final StringBuilder logBuffer = new StringBuilder();
    final long[] lastSaved = {0L};

    /**
     * Builds a listener that appends each ScriptLogger event onto inStep's "lastresponse" and saves it,
     * throttled to at most once every LOG_SAVE_INTERVAL_MS so a chatty skill (e.g. a CLI subprocess
     * gobbling stdout) doesn't hammer the database while it runs.
     * 
     * Subprocess will output to the ScriptLogger, which will be captured and appended to inStep's
     * "lastresponse".
     */
    public DataOutputSaver(MultiValued inStep, String inFieldName) {
        fieldSaveFieldName = inFieldName;
        inData = inStep;
    }

    public void handleLog(String inType, String inText, Throwable inEx)
    {
        synchronized (logBuffer)
        {
            if (logBuffer.length() > 0)
            {
                logBuffer.append("\n");
            }
            logBuffer.append(inText);

            String snapshot = logBuffer.toString();
            int max = Math.min(snapshot.length(), 30000); // Lucene limit

            if (max < snapshot.length())
            {
                int cutoff = snapshot.lastIndexOf('\n');
                if (cutoff > 0)
                {
                    max = cutoff + 1;
                }
            }
            snapshot = snapshot.substring(snapshot.length() - max, snapshot.length()); // saves in chunks
            inData.setValue(fieldSaveFieldName, snapshot);
            // Dont save it yet

            long now = System.currentTimeMillis();
            if (now - lastSaved[0] < LOG_SAVE_INTERVAL_MS)
            {
                return;
            }
            lastSaved[0] = now;
        }
        flush();
    }

    public void flush()
    {
        synchronized (logBuffer)
        {
            if (logBuffer.length() > 0)
            {
                getMediaArchive().saveData("agentjobstep", inData);
            }
        }
    }
}
