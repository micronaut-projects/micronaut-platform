package io.micronaut.build.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public abstract class SnapshotRewrite extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getVersions();

    @TaskAction
    public void rewriteLibsVersionToml() throws IOException {
        File file = getVersions().get().getAsFile();
        String text = SnapshotTransformer.replaceManagedMicronautVersionToSnapshots(file);
        DataOutputStream outstream= new DataOutputStream(new FileOutputStream(file,false));
        outstream.write(text.getBytes());
        outstream.close();
    }
}
