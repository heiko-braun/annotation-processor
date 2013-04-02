package org.bsc.maven.plugin.processor;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipFileObject extends SimpleJavaFileObject
{
    private ZipEntry zipEntry;
    private ZipFile zipFile;

    public ZipFileObject(ZipFile zipFile, ZipEntry zipEntry, URI uri)
    {
        super(uri, JavaFileObject.Kind.SOURCE);
        this.zipEntry = zipEntry;
        this.zipFile = zipFile;
    }

    public InputStream openInputStream() throws IOException
    {
        return this.zipFile.getInputStream(this.zipEntry);
    }

    public String getName()
    {
        return this.zipEntry.getName();
    }

    public CharSequence getCharContent(boolean b)
            throws IOException
    {
        InputStreamReader is = new InputStreamReader(openInputStream());
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(is);
        String read = br.readLine();

        while (read != null) {
            sb.append(read).append("\n");
            read = br.readLine();
        }

        return sb.toString();
    }

    public Reader openReader(boolean b) throws IOException
    {
        return new BufferedReader(new InputStreamReader(openInputStream()));
    }

    public long getLastModified()
    {
        return this.zipEntry.getTime();
    }

    public static ZipFileObject create(ZipFile zipFile, ZipEntry entry) {
        try {
            return new ZipFileObject(zipFile, entry, new URI("jar://" + zipFile.getName() + "!" + entry.getName()));
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException("Invalid zip entry:" + e.getMessage());
        }
    }
}