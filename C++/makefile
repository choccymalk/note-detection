# Compiler and flags
CXX = g++
CXXFLAGS = -std=c++17 -Wall -Wextra -O3
LDFLAGS = -pthread

# Directories
SRC_DIR = src
BUILD_DIR = build
INCLUDE_DIR = include

# Dependencies paths - adjust these according to your system
OPENCV_INCLUDE = -I/usr/include/opencv4
OPENCV_LIBS = -lopencv_core -lopencv_highgui -lopencv_imgproc -lopencv_videoio

TENSORFLOW_INCLUDE = -I/usr/local/include/tensorflow/lite
TENSORFLOW_LIBS = -ltensorflowlite -ltensorflowlite_c

CROW_INCLUDE = -I/usr/local/include
CROW_LIBS = -lboost_system -lboost_filesystem

JSON_INCLUDE = -I/usr/local/include/nlohmann

# Edge TPU paths
EDGETPU_INCLUDE = -I/usr/local/include/edgetpu
EDGETPU_LIBS = -ledgetpu

# Combine all includes and libraries
INCLUDES = -I$(INCLUDE_DIR) $(OPENCV_INCLUDE) $(TENSORFLOW_INCLUDE) $(CROW_INCLUDE) $(JSON_INCLUDE) $(EDGETPU_INCLUDE)
LIBS = $(OPENCV_LIBS) $(TENSORFLOW_LIBS) $(CROW_LIBS) $(EDGETPU_LIBS)

# Source files
SRCS = $(wildcard $(SRC_DIR)/*.cpp)
OBJS = $(SRCS:$(SRC_DIR)/%.cpp=$(BUILD_DIR)/%.o)

# Main target
TARGET = detection_app

# Default target
all: directories $(TARGET)

# Create build directory
directories:
	@mkdir -p $(BUILD_DIR)

# Main executable
$(TARGET): $(OBJS)
	$(CXX) $(OBJS) -o $@ $(LDFLAGS) $(LIBS)

# Object files
$(BUILD_DIR)/%.o: $(SRC_DIR)/%.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

# Install dependencies (Ubuntu/Debian)
install-deps:
	sudo apt-get update
	sudo apt-get install -y \
		build-essential \
		cmake \
		libopencv-dev \
		libboost-all-dev \
		python3-pip \
		libasio-dev
	pip3 install tensorflow
	# Install Edge TPU runtime
	wget https://github.com/google-coral/libedgetpu/releases/download/release-grouper/edgetpu_runtime_20221024.zip
	unzip edgetpu_runtime_20221024.zip
	cd edgetpu_runtime
	sudo ./install.sh
	cd ../
	# Install Crow (from source)
	git clone https://github.com/CrowCpp/Crow.git
	cd Crow && mkdir build && cd build && cmake .. && make && sudo make install
	# Install nlohmann/json
	sudo apt-get install -y nlohmann-json3-dev

# Clean build files
clean:
	rm -rf $(BUILD_DIR) $(TARGET)

# Install the application
install: $(TARGET)
	sudo cp $(TARGET) /usr/local/bin/
	sudo mkdir -p /usr/local/share/detection_app/templates
	sudo cp templates/* /usr/local/share/detection_app/templates/

# Uninstall the application
uninstall:
	sudo rm -f /usr/local/bin/$(TARGET)
	sudo rm -rf /usr/local/share/detection_app

# Run the application
run: $(TARGET)
	./$(TARGET)

# Debug build
debug: CXXFLAGS += -g -DDEBUG
debug: clean all

# Release build
release: CXXFLAGS += -O3 -DNDEBUG
release: clean all

# Generate documentation using Doxygen
docs:
	doxygen Doxyfile

.PHONY: all clean install uninstall run debug release docs install-deps directories

# Dependencies
$(BUILD_DIR)/%.o: $(SRC_DIR)/%.cpp $(INCLUDE_DIR)/%.hpp
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

# Expected directory structure:
#   project_root/
#     ├── Makefile
#     ├── src/
#     │   └── *.cpp files
#     ├── include/
#     │   └── *.hpp files
#     ├── build/
#     │   └── *.o files
#     └── templates/
#         └── *.html files
