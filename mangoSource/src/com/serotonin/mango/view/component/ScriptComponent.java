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
package com.serotonin.mango.view.component;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.serotonin.json.JsonRemoteEntity;
import com.serotonin.json.JsonRemoteProperty;
import com.serotonin.mango.DataTypes;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.rt.dataSource.meta.ScriptExecutor;
import com.serotonin.mango.view.ImplDefinition;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.web.dwr.BaseDwr;
import com.serotonin.mango.web.taglib.Functions;
import com.serotonin.util.SerializationHelper;

/**
 * @author Matthew Lohbihler
 */
@JsonRemoteEntity
public class ScriptComponent extends PointComponent {
    public static ImplDefinition DEFINITION = new ImplDefinition("script", "SCRIPT", "graphic.script", new int[] {
            DataTypes.BINARY, DataTypes.MULTISTATE, DataTypes.NUMERIC, DataTypes.ALPHANUMERIC });

    private static final String SCRIPT_PREFIX = "function __scriptRenderer__() {";
    private static final String SCRIPT_SUFFIX = "\r\n}\r\n__scriptRenderer__();";

    @JsonRemoteProperty
    private String script;

    @Override
    public String snippetName() {
        return "scriptContent";
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    @Override
    public void addDataToModel(Map<String, Object> model, PointValueTime value) {
        String result;

        if (value == null)
            result = "--";
        else {
            // Create the script engine.
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("js");

            DataPointVO point = tgetDataPoint();

            // Put the values into the engine scope.
            engine.put("value", value.getValue().getObjectValue());
            engine.put("htmlText", Functions.getHtmlText(point, value));
            engine.put("renderedText", Functions.getRenderedText(point, value));
            engine.put("time", value.getTime());
            engine.put("pointComponent", this);
            engine.put("point", point);
            // Copy properties from the model into the engine scope.
            engine.put(BaseDwr.MODEL_ATTR_EVENTS, model.get(BaseDwr.MODEL_ATTR_EVENTS));
            engine.put(BaseDwr.MODEL_ATTR_HAS_UNACKED_EVENT, model.get(BaseDwr.MODEL_ATTR_HAS_UNACKED_EVENT));
            engine.put(BaseDwr.MODEL_ATTR_RESOURCE_BUNDLE, model.get(BaseDwr.MODEL_ATTR_RESOURCE_BUNDLE));

            // Create the script.
            String evalScript = SCRIPT_PREFIX + script + SCRIPT_SUFFIX;

            // Execute.
            try {
                Object o = engine.eval(evalScript);
                if (o == null)
                    result = null;
                else
                    result = o.toString();
            }
            catch (ScriptException e) {
                e = ScriptExecutor.prettyScriptMessage(e);
                result = e.getMessage();
            }
        }

        model.put("scriptContent", result);
    }

    @Override
    public ImplDefinition definition() {
        return DEFINITION;
    }

    //
    // /
    // / Serialization
    // /
    //
    private static final long serialVersionUID = -1;
    private static final int version = 1;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(version);

        SerializationHelper.writeSafeUTF(out, script);
    }

    private void readObject(ObjectInputStream in) throws IOException {
        int ver = in.readInt();

        // Switch on the version of the class so that version changes can be elegantly handled.
        if (ver == 1)
            script = SerializationHelper.readSafeUTF(in);
    }
}
