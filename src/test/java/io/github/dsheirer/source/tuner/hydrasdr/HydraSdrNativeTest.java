/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.source.tuner.hydrasdr;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HydraSdrNativeTest
{
    @Test
    void resolvesPackagedNativeDirectoryFromApplicationJar(@TempDir Path applicationRoot) throws Exception
    {
        Path applicationDirectory = Files.createDirectories(applicationRoot.resolve("lib/app"));
        File applicationJar = Files.createFile(applicationDirectory.resolve("sdrtrunk.jar")).toFile();

        assertTrue(HydraSdrNative.getBundledLibraryDirectories(applicationJar)
            .contains(applicationRoot.resolve("lib/native").toFile()));
    }
}
