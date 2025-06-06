/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.avro.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.avro.SystemLimitException;
import org.apache.avro.TestSystemLimitException;
import org.junit.jupiter.api.Test;

public class TestUtf8 {
  @Test
  void byteConstructor() throws Exception {
    byte[] bs = "Foo".getBytes(StandardCharsets.UTF_8);
    Utf8 u = new Utf8(bs);
    assertEquals(bs.length, u.getByteLength());
    for (int i = 0; i < bs.length; i++) {
      assertEquals(bs[i], u.getBytes()[i]);
    }
  }

  @Test
  void arrayReusedWhenLargerThanRequestedSize() {
    byte[] bs = "55555".getBytes(StandardCharsets.UTF_8);
    Utf8 u = new Utf8(bs);
    assertEquals(5, u.getByteLength());
    byte[] content = u.getBytes();
    u.setByteLength(3);
    assertEquals(3, u.getByteLength());
    assertSame(content, u.getBytes());
    u.setByteLength(4);
    assertEquals(4, u.getByteLength());
    assertSame(content, u.getBytes());
  }

  @Test
  void hashCodeReused() {
    assertEquals(1, new Utf8().hashCode());
    assertEquals(128, new Utf8("a").hashCode());
    assertEquals(4865, new Utf8("zz").hashCode());
    assertEquals(153, new Utf8("z").hashCode());
    assertEquals(127791473, new Utf8("hello").hashCode());
    assertEquals(4122302, new Utf8("hell").hashCode());

    Utf8 u = new Utf8("a");
    assertEquals(128, u.hashCode());
    assertEquals(128, u.hashCode());

    u.set("a");
    assertEquals(128, u.hashCode());

    u.setByteLength(1);
    assertEquals(128, u.hashCode());
    u.setByteLength(2);
    assertNotEquals(128, u.hashCode());

    u.set("zz");
    assertEquals(4865, u.hashCode());
    u.setByteLength(1);
    assertEquals(153, u.hashCode());

    u.set("hello");
    assertEquals(127791473, u.hashCode());
    u.setByteLength(4);
    assertEquals(4122302, u.hashCode());

    u.set(new Utf8("zz"));
    assertEquals(4865, u.hashCode());
    u.setByteLength(1);
    assertEquals(153, u.hashCode());

    u.set(new Utf8("hello"));
    assertEquals(127791473, u.hashCode());
    u.setByteLength(4);
    assertEquals(4122302, u.hashCode());
  }

  /**
   * There are two different code paths that hashcode() can call depending on the
   * state of the internal buffer. If the buffer is full (string length is equal
   * to buffer length) then the JDK hashcode function can be used. However, if the
   * buffer is not full (string length is less than the internal buffer length),
   * then the JDK does not support this prior to JDK 23 and a scalar
   * implementation is the only option today. This difference can be resolved with
   * JDK 23 as it supports both cases.
   */
  @Test
  void hashCodeBasedOnCapacity() {
    // string = 8; buffer = 8
    Utf8 fullCapacity = new Utf8("abcdefgh", 8);

    // string = 8; buffer = 9
    Utf8 partialCapacity = new Utf8("abcdefghX", 8);

    assertEquals(fullCapacity.hashCode(), partialCapacity.hashCode());
  }

  @Test
  void oversizeUtf8() {
    Utf8 u = new Utf8();
    u.setByteLength(1024);
    assertEquals(1024, u.getByteLength());
    assertThrows(UnsupportedOperationException.class,
        () -> u.setByteLength(TestSystemLimitException.MAX_ARRAY_VM_LIMIT + 1));

    try {
      System.setProperty(SystemLimitException.MAX_STRING_LENGTH_PROPERTY, Long.toString(1000L));
      TestSystemLimitException.resetLimits();

      Exception ex = assertThrows(SystemLimitException.class, () -> u.setByteLength(1024));
      assertEquals("String length 1024 exceeds maximum allowed", ex.getMessage());
    } finally {
      System.clearProperty(SystemLimitException.MAX_STRING_LENGTH_PROPERTY);
      TestSystemLimitException.resetLimits();
    }
  }

  @Test
  void serialization() throws IOException, ClassNotFoundException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {

      Utf8 originalEmpty = new Utf8();
      Utf8 originalBytes = new Utf8("originalBytes".getBytes(StandardCharsets.UTF_8));
      Utf8 originalString = new Utf8("originalString");

      oos.writeObject(originalEmpty);
      oos.writeObject(originalBytes);
      oos.writeObject(originalString);
      oos.flush();

      try (ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
          ObjectInputStream ois = new ObjectInputStream(bis)) {
        assertThat(ois.readObject(), is(originalEmpty));
        assertThat(ois.readObject(), is(originalBytes));
        assertThat(ois.readObject(), is(originalString));
      }
    }
  }

}
