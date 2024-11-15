package io.telicent;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Paths;

public class DockerTestDatasetBackupsContainer extends GenericContainer<DockerTestDatasetBackupsContainer> {

    private static ImageFromDockerfile dockerImage;

    private static DockerTestDatasetBackupsContainer container;

    private DockerTestDatasetBackupsContainer() {
        super(dockerImage);
    }

    public static DockerTestDatasetBackupsContainer getInstance() {
        if (container == null) {
            dockerImage = new ImageFromDockerfile()
                    .withDockerfile(Paths.get("../Dockerfile-backup-tests"));

            container = new DockerTestDatasetBackupsContainer();
            container.withExposedPorts(3030);

        }
        return container;
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

}
