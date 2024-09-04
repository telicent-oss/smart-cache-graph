package io.telicent.core;

import org.apache.jena.atlas.lib.Timer;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.sys.TDBInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static java.lang.String.format;

public class FMod_InitialCompaction implements FusekiAutoModule {

    public static final Logger LOG = LoggerFactory.getLogger("io.telicent.core.FMod_InitialCompaction");
    final Set<String> datasets = new HashSet<>();
    final boolean DELETE_OLD = true;
    public static final String DISABLE_INITIAL_COMPACTION = "DISABLE_INITIAL_COMPACTION";
    private static final String VERSION = Version.versionForClass(FMod_InitialCompaction.class).orElse("<development>");

    @Override
    public String name() {
        return "Initial Compaction";
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> names, Model configModel) {
        FmtLog.info(Fuseki.configLog, "%s Fuseki Module (%s)", name(), VERSION);
        this.datasets.addAll(names);
    }

    /**
     * Compact the existing TDB2 after initial data-load
     * @param server underlying Fuseki server
     * Note: We will only run this module at server start-up.
     */
    @Override
    public void serverBeforeStarting(FusekiServer server) {
        for (String name : datasets) {
            Optional<DatasetGraph> optionalDatasetGraph = FKS.findDataset(server, name);
            if (optionalDatasetGraph.isPresent()) {
                DatasetGraph dsg = getTDB2(optionalDatasetGraph.get());
                if (dsg != null) {
                    FmtLog.info(LOG, format("[Initial] >>>> Start compact %s", name));
                    Timer timer = new Timer();
                    timer.startTimer();
                    DatabaseMgr.compact(dsg, DELETE_OLD);
                    FmtLog.info(LOG, format("[Initial] <<<< Finish compact %s. Took %s seconds", name, Timer.timeStr(timer.endTimer())));
                } else {
                    FmtLog.debug(LOG, format("Compaction not required for %s as not TDB2",name));
                }
            } else {
                FmtLog.debug(LOG, format("Compaction not required for %s as no graph",name));
            }
        }

    }

    /**
     * Check the given Graph and, if possible, return the underlying TDB2 instance
     * @param dsg Graph
     * @return TDB2 compatible DSG or null
     */
    public static DatasetGraph getTDB2(DatasetGraph dsg) {
        for (; ;) {
            if (IS_TDB_2.test(dsg))
                return dsg;
            if (!(dsg instanceof DatasetGraphWrapper dsgw))
                return null;
            dsg = dsgw.getWrapped();
        }
    }

    private static final Predicate<DatasetGraph> IS_TDB_2 = TDBInternal::isTDB2;
}
