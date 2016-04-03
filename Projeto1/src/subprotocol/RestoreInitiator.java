package subprotocol;

import communication.Message;
import communication.MessageParser;
import communication.message.ChunkMessage;
import communication.message.GetChunkMessage;
import general.FilesMetadataManager;
import general.Logger;
import general.MalformedMessageException;
import general.MulticastChannel;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Afonso on 02/04/2016.
 */
public class RestoreInitiator implements Observer {
    private static final int MAXCHUNKSIZE = 64 * 1000;
    private static final int MAX_ATTEMPTS = 5;
    private final MulticastChannel mcChannel;
    private final MulticastChannel mdrChannel;
    private final String filePath;
    private final String fileId;
    private final String localId;
    private final List<Integer> chunksToReceive;
    private RandomAccessFile file;

    public RestoreInitiator(String filePath, String localId, MulticastChannel mcChannel, MulticastChannel mdrChannel) throws IOException {
        Path path = Paths.get(filePath);
        this.filePath = path.getParent() + File.separator + "Restore" + localId + '_' + path.getFileName();
        this.localId = localId;
        this.mcChannel = mcChannel;
        this.mdrChannel = mdrChannel;
        long fileSize = new File(filePath).length();
        int totalChunks = (int) (fileSize / MAXCHUNKSIZE) + 1;
        this.chunksToReceive = Collections.synchronizedList(IntStream.range(0, totalChunks).boxed().collect(Collectors.toList()));
        this.file = new RandomAccessFile(this.filePath, "rw");
        this.fileId = FilesMetadataManager.getInstance().getFileId(filePath);
    }

    public void getChunks() throws IOException {
        mdrChannel.addObserver(this);
        this.file.setLength(0);
        int attempts;
        for (attempts = 0; attempts < MAX_ATTEMPTS; ++attempts) {
            synchronized (this) {
                for (int i : chunksToReceive) {
                    byte[] message = new GetChunkMessage(localId, fileId, "" + i).getBytes();
                    mcChannel.send(message);
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            synchronized (this) {
                if (chunksToReceive.size() == 0)
                    break;
            }

        }
        mdrChannel.deleteObserver(this);
        file.close();

        if (attempts == MAX_ATTEMPTS) {
            new File(filePath).delete();
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        MessageParser parser = new ChunkMessage.Parser();
        Message message;
        try {
            message = parser.parse((byte[])arg);
        } catch (IOException | MalformedMessageException e) {
            return;
        }
        if(!message.getFileId().equals(fileId))
            return;
        int chunkNo = Integer.parseInt(message.getChunkNo());

        synchronized (this) {
            if (chunksToReceive.contains(chunkNo)) {
                try {
                    file.write(message.getBody(), chunkNo * MAXCHUNKSIZE, message.getBody().length);
                    chunksToReceive.remove(chunkNo);
                } catch (IOException ignored) {}
            }
        }

    }
}
