/*
 * Copyright (c) Facebook, Inc. and its affiliates.
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

package com.facebook.buck.util.cache.impl;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.util.cache.HashCodeAndFileType;
import com.facebook.buck.util.cache.JarContentHasher;
import com.facebook.buck.util.zip.CustomJarOutputStream;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

class DefaultJarContentHasher implements JarContentHasher {

  private final ProjectFilesystem filesystem;
  private final Path jarRelativePath;

  public DefaultJarContentHasher(ProjectFilesystem filesystem, Path jarRelativePath) {
    Preconditions.checkState(!jarRelativePath.isAbsolute());
    this.filesystem = filesystem;
    this.jarRelativePath = jarRelativePath;
  }

  @Override
  public Path getJarRelativePath() {
    return jarRelativePath;
  }

  @Override
  public ImmutableMap<String, HashCodeAndFileType> getContentHashes() throws IOException {
    Manifest manifest = null;
    try (JarInputStream inputStream =
        new JarInputStream(filesystem.newFileInputStream(jarRelativePath))) {
      JarEntry entry = inputStream.getNextJarEntry();
      while (entry != null) {
        if (JarFile.MANIFEST_NAME.equalsIgnoreCase(entry.getName())) {
          manifest = new Manifest();
          manifest.read(inputStream);
          break;
        }
        entry = inputStream.getNextJarEntry();
      }
    }
    if (manifest == null) {
      throw new UnsupportedOperationException(
          "Cache does not know how to return hash codes for archive members except "
              + "when the archive contains a META-INF/MANIFEST.MF with "
              + CustomJarOutputStream.DIGEST_ATTRIBUTE_NAME
              + " attributes for each file.");
    }


    ImmutableMap.Builder<String, HashCodeAndFileType> builder = ImmutableMap.builder();
    for (Map.Entry<String, Attributes> nameAttributesEntry : manifest.getEntries().entrySet()) {
      Attributes attributes = nameAttributesEntry.getValue();
      String hashStringValue = attributes.getValue(CustomJarOutputStream.DIGEST_ATTRIBUTE_NAME);
      if (hashStringValue == null) {
        continue;
      }

      HashCode memberHash = HashCode.fromString(hashStringValue);
      HashCodeAndFileType memberHashCodeAndFileType = HashCodeAndFileType.ofFile(memberHash);

      builder.put(nameAttributesEntry.getKey(), memberHashCodeAndFileType);
    }

    return builder.build();
  }
}
