/*
 * Tencent is pleased to support the open source community by making
 * Firestorm-Spark remote shuffle server available. 
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * https://opensource.org/licenses/Apache-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.tencent.rss.storage.handler.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.tencent.rss.common.util.ChecksumUtils;
import com.tencent.rss.storage.HdfsTestBase;
import com.tencent.rss.storage.common.FileBasedShuffleSegment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import org.apache.hadoop.fs.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class HdfsFileReaderTest extends HdfsTestBase {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void createStreamTest() throws IOException {
    Path path = new Path(HDFS_URI, "createStreamTest");
    fs.create(path);

    try (HdfsFileReader reader = new HdfsFileReader(path, conf)) {
      assertTrue(fs.isFile(path));
      assertEquals(0L, reader.getOffset());
    }

    fs.deleteOnExit(path);
  }

  @Test
  public void createStreamAppendTest() throws IOException {
    Path path = new Path(HDFS_URI, "createStreamFirstTest");

    assertFalse(fs.isFile(path));
    try {
      new HdfsFileReader(path, conf);
      fail("Exception should be thrown");
    } catch (IllegalStateException ise) {
      ise.getMessage().startsWith(HDFS_URI + "createStreamFirstTest don't exist");
    }
  }

  @Test
  public void readDataTest() throws IOException {
    Path path = new Path(HDFS_URI, "readDataTest");
    byte[] data = new byte[160];
    int offset = 128;
    int length = 32;
    new Random().nextBytes(data);
    long crc11 = ChecksumUtils.getCrc32(ByteBuffer.wrap(data, offset, length));

    try (HdfsFileWriter writer = new HdfsFileWriter(path, conf)) {
      writer.writeData(data);
    }
    FileBasedShuffleSegment segment = new FileBasedShuffleSegment(23, offset, length, length, 0xdeadbeef, 1);
    try (HdfsFileReader reader = new HdfsFileReader(path, conf)) {
      byte[] actual = reader.read(segment.getOffset(), segment.getLength());
      long crc22 = ChecksumUtils.getCrc32(actual);

      for (int i = 0; i < length; ++i) {
        assertEquals(data[i + offset], actual[i]);
      }
      assertEquals(crc11, crc22);
      // EOF exception is expected
      segment = new FileBasedShuffleSegment(23, offset * 2, length, length, 1, 1);
      assertEquals(0, reader.read(segment.getOffset(), segment.getLength()).length);
    }
  }
}
