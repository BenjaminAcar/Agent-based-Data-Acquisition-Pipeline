package de.gtarc.opaca.sample


import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.util.ConfigLoader
import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem


fun main() {
    val image = ConfigLoader.loadContainerImageFromResources("/container.json")
    agentSystem("opaca-sample-container") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            add(DataOrchestratorAgent("DataOrchestratorAgent"))
        }
    }.start()
}
