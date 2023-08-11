/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.buildcache;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

public class TestMojo implements Mojo {

    private boolean bool;
    private int primitive;
    private File file;
    private Path path;
    private List<String> list;
    private String[] array;

    private Object anyObject;
    private final Object nullObject = null;

    public TestMojo() {}

    public TestMojo(boolean bool, int primitive, File file, Path path, List<String> list, String[] array) {

        this.bool = bool;
        this.primitive = primitive;
        this.file = file;
        this.path = path;
        this.list = list;
        this.array = array;
    }

    public static TestMojo create(
            boolean bool, int primitive, File file, Path path, List<String> list, String[] array) {
        return new TestMojo(bool, primitive, file, path, list, array);
    }

    public boolean isBool() {
        return bool;
    }

    public void setBool(boolean bool) {
        this.bool = bool;
    }

    public int getPrimitive() {
        return primitive;
    }

    public void setPrimitive(int primitive) {
        this.primitive = primitive;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public List<String> getList() {
        return list;
    }

    public void setList(List<String> list) {
        this.list = list;
    }

    public String[] getArray() {
        return array;
    }

    public void setArray(String[] array) {
        this.array = array;
    }

    public Object getAnyObject() {
        return anyObject;
    }

    public void setAnyObject(Object anyObject) {
        this.anyObject = anyObject;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // do nothing
    }

    @Override
    public void setLog(Log log) {
        // do nothing
    }

    @Override
    public Log getLog() {
        // do nothing
        return null;
    }
}
