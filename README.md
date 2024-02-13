# Agent-Based Data Acquisition Pipeline

This repository contains an advanced agent-based data acquisition pipeline developed using the OPACA framework, designed to efficiently capture RTSP frames, process the data, orchestrate the capturing process, and facilitate data transfer to the cloud. Built with Java version "21.0.1" (2023-10-17 LTS), this solution leverages modern software practices to ensure reliable and scalable data management.

## Prerequisites

- Java version "21.0.1" 2023-10-17 LTS
- Docker
- Access to an RTSP camera
- OPACA platform deployed on your machine
- Access to an microservice that is able to process raw images (blurring)

## Setup and Deployment

### Deploying the OPACA Platform

Before deploying the acquisition pipeline, ensure that the OPACA platform is properly deployed on your machine. This platform serves as the foundation for running the data and orchestrator containers.

### Building Container Images

1. **Data Container Image**

   Navigate to the `data-container` directory and build the data agent container image:

   ```
   docker build -t data-agent-container-image . ```

2. **Orchestrator Container Image**

   Then, go to the `data-orchestrator-container` directory to build the orchestrator agent container image:

   ```
   docker build -t orchestrator-agent-container-image .
    ```


### Deploying the Pipeline

1. **Spawn the Data Container**

   Use the following curl command to trigger the OPACA platform to deploy the data agent container:
   ```
    curl -X POST -H "Content-Type: application/json" -d '{"image": {"imageName": "data-agent-container-image"}}' http://<IP of OPACA platform>:<OPACA platform port>/containers

   ```

1. **Spawn the Orchestrator Container**

   Use the following curl command to trigger the OPACA platform to deploy the data orchestrator container:
   ```
    curl -X POST -H "Content-Type: application/json" -d '{"image": {"imageName": "orchestrator-agent-container-image"}}' http://<IP of OPACA platform>:<OPACA platform port>/containers

   ```

2. **Deploy Agents**

    Deploy the necessary agents within the agent container by triggering two routes. For capturing and processing:
   ```
    curl -X POST -H "Content-Type: application/json" -d '{"name": "agent-1"}' http://<IP of OPACA platform>:<OPACA platform port>/invoke/SpawnCaptureAndProcessAgent

   ```
   For file management:
    ```
    curl -X POST -H "Content-Type: application/json" -d '{"name": "agent-2"}' http://<IP of OPACA platform>:<OPACA platform port>/invoke/SpawnFileManagerAgent
    ```
    Choose descriptive names (e.g., "agent-1", "agent-2") for your agents to maintain a structured system, especially when deploying multiple agent containers for data acquisition.

3. **Notify the OPACA Platform**

    After deploying the agents, notify the OPACA platform of the changes made within the agent container:
   ```
    curl -X POST -H "Content-Type: application/json" -d '{"id": "<OPACA ID of the agent container>"}' http://<IP of OPACA platform>:<OPACA platform port>/containers/notify
   ```

1. **Triggering the Acquisition Process without orchestration**

   Start the acquisition process with the following command, ensuring to provide the necessary parameters:
   ```
    curl -X POST -H "Content-Type: application/json" -d '{"rtspUrl": "<rtsp camera address>", "processingUrl": "<processing URL>", "streamSeconds": 10, "frameRate": 1, "startTime": "YYYY-MM-DDTHH:mm"}' http://<IP of OPACA platform>:<OPACA platform port>/invoke/CaptureAndProcessData?timeout=120000
   ```

    - rtspUrl: Address of the RTSP camera (e.g., rtsp://admin:admin@192.168.1.1:554)
    - processingUrl: Address for the microservice that processes the image (e.g., blurring)
    - frameRate: Number of images captured per second
    - streamSeconds: Duration of the stream in seconds
    - startTime: Start time for the acquisition process in YYYY-MM-DDTHH:mm format


### Orchestrating the acquisiton process

1. **Get information about files**

   To get information about the current files for a specific rtsp camera, use the following command:

   ```
   curl -X POST -H "Content-Type: application/json" -d '{"rtspUrl":"<rtsp camera address>"}' http://<IP of OPACA platform>:<OPACA platform port>/invoke/GetFilesForCameraID
   ```

   If needed, specify also the OPACA container ID if you want to request a specific one. For details about how to do that, please have a look in the OPACA documentation.

2. **Trigger Acqusition by using the Orchestrator Agent**

   To trigger the acqusition, you have to trigger the orchestrator agent, pass the agent that should capture the data and also the necessary parameters for the CaptureAndProcessData action.

   ```
   curl -X POST -H "Content-Type: application/json" -d '{"action": "CaptureAndProcessData", "agentId": "agent that is responsible for a specific camera", "rtspUrl":"<rtsp camera address>", "processingUrl": "<processing URL>", "streamSeconds": 10, "frameRate": 1, "startTime": "YYYY-MM-DDTHH:mm"}' "http://<IP of OPACA platform>:<OPACA platform port>/invoke/TriggerAcquisition?timeout=120000"
   ```


3. **Move the data to the Orchestrator Agent / cloud**

   To move the data to the location of the orchestrator (he should be on the cloud node), we can use the respective endpoint that is provided by the Data Stream Agent

   ```
   curl -X POST -H "Content-Type: application/json" -d '{"containerId":"<OPACA container ID where the data is lying>", "agentId":"<ID of the Data Stream Agent>", "fileName": "<name of the file that will be stored on the orchestrator node>"}' "http://<IP of OPACA platform>:<OPACA platform port>/invoke/DownloadResults"
   ```



