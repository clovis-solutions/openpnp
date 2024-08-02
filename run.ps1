# Start script for Docker container with ROS2
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Write-Host "Please run this script as an administrator."
    exit 1
}

$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Definition
Write-Host "Root directory: $ROOT"

# Set display environment variable
$DISPLAY_DEVICE = "-e DISPLAY=host.docker.internal:0.0 -v /tmp/.X11-unix/:/tmp/.X11-unix"
Write-Host "Using display: host.docker.internal:0.0"

# Path to VcXsrv configuration file
$vcxsrvConfigPath = "C:\clovis-solutions\openpnp\config.xlaunch"

# Check if XLaunch (VcXsrv) is running and start it with the config file if necessary
$vcxsrvProcess = Get-Process -Name "vcxsrv" -ErrorAction SilentlyContinue
if (-not $vcxsrvProcess) {
    Write-Host "VcXsrv is not running. Attempting to start VcXsrv with configuration file..."
    Start-Process $vcxsrvConfigPath
    Start-Sleep -Seconds 5 # Wait for VcXsrv to start
}

# Check if Docker Desktop is running and start it if necessary
$dockerProcess = Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
if (-not $dockerProcess) {
    Write-Host "Docker Desktop is not running. Attempting to start Docker Desktop..."
    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    Start-Sleep -Seconds 5 # Wait for Docker Desktop to start
}

# Check if Docker is running with elevated privileges
try {
    docker version > $null
} catch {
    Write-Host "Docker does not seem to be running or is not running with elevated privileges."
    Write-Host "Please ensure Docker Desktop is running and restart it as an administrator."
    exit 1
}

# Run the container
$ARCH = (Get-WmiObject -Class Win32_ComputerSystem).SystemType
$CONTAINER_NAME = "test"
$WORKDIR = "app"

if ($ARCH -eq "ARM64") {
    $CONTAINER = "test"
    if (docker ps -a --format '{{.Names}}' | Select-String -Pattern "^$CONTAINER_NAME$") {
        if (docker ps -f "name=$CONTAINER_NAME" --format '{{.Status}}' | Select-String -Pattern "Up") {
            Write-Host "Entering running container."
            docker exec -it $CONTAINER_NAME bash
        } else {
            Write-Host "Starting container instance."
            docker start -it $CONTAINER_NAME
        }
    } else {
        Write-Host "Starting new container instance: docker run -it --rm --runtime nvidia --name $CONTAINER_NAME --network host --privileged --volume ${ROOT}:/${WORKDIR} $DISPLAY_DEVICE $V4L2_DEVICES $CONTAINER"
        docker run -it --rm --runtime nvidia --name $CONTAINER_NAME --network host --privileged --volume ${ROOT}:/${WORKDIR} $DISPLAY_DEVICE $V4L2_DEVICES $CONTAINER
    }
} elseif ($ARCH -eq "x64-based PC") {
    $CONTAINER = "test"
    if (docker ps -a --format '{{.Names}}' | Select-String -Pattern "^$CONTAINER_NAME$") {
        if (docker ps -f "name=$CONTAINER_NAME" --format '{{.Status}}' | Select-String -Pattern "Up") {
            Write-Host "Entering running container."
            docker exec -it $CONTAINER_NAME bash
        } else {
            Write-Host "Starting container instance."
            docker start -ai $CONTAINER_NAME
        }
    } else {
        Write-Host "Starting new container instance: docker run -it --rm --gpus all --name $CONTAINER_NAME --network host --privileged --volume ${ROOT}:/${WORKDIR} $DISPLAY_DEVICE $CONTAINER"
        docker run -it --rm --gpus all --name $CONTAINER_NAME --network host --privileged --volume ${ROOT}:/${WORKDIR} -e DISPLAY=host.docker.internal:0.0 -v /tmp/.X11-unix/:/tmp/.X11-unix $CONTAINER
    }
}
