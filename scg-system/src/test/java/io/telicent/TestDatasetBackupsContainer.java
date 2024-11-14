package io.telicent;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Paths;

public class TestDatasetBackupsContainer extends GenericContainer<TestDatasetBackupsContainer> {

    private static ImageFromDockerfile dockerImage;

    private static TestDatasetBackupsContainer container;

    private TestDatasetBackupsContainer() {
        super(dockerImage);
    }

    public static TestDatasetBackupsContainer getInstance() {
        if (container == null) {
            dockerImage = new ImageFromDockerfile()
                    .withDockerfile(Paths.get("../Dockerfile-backup-tests"));

            container = new TestDatasetBackupsContainer();
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
