

## Running the sample

### Base Platform Environment

Install and run the [Platform sample](https://github.com/ForgeRock/forgeops/tree/master/samples/fr-platform)

### Import AM configuration for this sample

```
kubectl cp amster/ amster:/tmp/da
kubectl exec -it amster ./amster /tmp/da/scripts/install_da.amster
```

### Serve the application for this sample

The easiest way to execute this sample is by using Docker. This will automate the download and setup of your Vert.x execution environment.

    docker build -t daclient:latest client
    docker run -d --network host daclient:latest

    docker build -t darp:latest rp
    docker run -d --network host darp:latest


Now you can access the application with http://localhost:8080
