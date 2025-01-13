#include <opencv2/opencv.hpp>
#include <tensorflow/lite/interpreter.h>
#include <tensorflow/lite/kernels/register.h>
#include <tensorflow/lite/model.h>
#include <tensorflow/lite/optional_debug_tools.h>
#include <crow.hpp>
#include <nlohmann/json.hpp>
#include <thread>
#include <fstream>
#include <sstream>
#include <map>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <filesystem>

using json = nlohmann::json;
namespace fs = std::filesystem;

// Constants
const char* CONFIG_FILE = "config.json";
const char* MODEL_PATH = "note_detect_v2-fp16_edgetpu.tflite";
const char* LABELS_PATH = "labels.txt";
const char* TEMPLATE_DIR = "templates/";
const int INPUT_SIZE = 320;
const std::vector<std::string> SETTING1_OPTIONS = {"option1", "option2", "option3", "option4"};

// Template engine class
class TemplateEngine {
private:
    std::map<std::string, std::string> templates;

    std::string loadTemplate(const std::string& name) {
        std::ifstream file(TEMPLATE_DIR + name);
        if (!file.is_open()) {
            throw std::runtime_error("Template not found: " + name);
        }
        return std::string(std::istreambuf_iterator<char>(file),
                          std::istreambuf_iterator<char>());
    }

    std::string replaceAll(std::string str, const std::string& from, const std::string& to) {
        size_t pos = 0;
        while ((pos = str.find(from, pos)) != std::string::npos) {
            str.replace(pos, from.length(), to);
            pos += to.length();
        }
        return str;
    }

public:
    TemplateEngine() {
        // Load all templates at startup
        for (const auto& entry : fs::directory_iterator(TEMPLATE_DIR)) {
            if (entry.path().extension() == ".html") {
                std::string name = entry.path().filename().string();
                templates[name] = loadTemplate(name);
            }
        }
    }

    std::string render(const std::string& templateName, const json& data) {
        std::string result = templates[templateName];
        
        // Replace variables in the template
        for (auto& [key, value] : data.items()) {
            std::string placeholder = "{{" + key + "}}";
            if (value.is_string()) {
                result = replaceAll(result, placeholder, value.get<std::string>());
            } else if (value.is_number()) {
                result = replaceAll(result, placeholder, std::to_string(value.get<int>()));
            } else if (value.is_array()) {
                // Handle arrays (like camera options and setting1 options)
                std::string replacement;
                if (key == "setting1_options" || key == "available_cameras") {
                    for (const auto& item : value) {
                        replacement += "<option value=\"" + item.get<std::string>() + "\">" +
                                     item.get<std::string>() + "</option>\n";
                    }
                }
                result = replaceAll(result, placeholder, replacement);
            }
        }
        return result;
    }
};

// Configuration structure
struct Config {
    std::string ipOfRio;
    int cameraIndex;

    static Config loadConfig() {
        std::ifstream file(CONFIG_FILE);
        if (!file.is_open()) {
            // Create default config if file doesn't exist
            Config defaultConfig{"10.0.0.2", 0};
            saveConfig(defaultConfig);
            return defaultConfig;
        }
        json j;
        file >> j;
        return Config{j["ipOfRio"], j["camera_index"]};
    }

    static void saveConfig(const Config& config) {
        json j;
        j["ipOfRio"] = config.ipOfRio;
        j["camera_index"] = config.cameraIndex;
        std::ofstream file(CONFIG_FILE);
        file << j.dump(4);
    }
};


// Detection result structure
struct Detection {
    float xmin, ymin, xmax, ymax;
    float confidence;
    std::string className;

    json toJson() const {
        return {
            {"xmin", xmin},
            {"ymin", ymin},
            {"xmax", xmax},
            {"ymax", ymax},
            {"confidence", confidence},
            {"class", className}
        };
    }
};

class ObjectDetector {
private:
    std::unique_ptr<tflite::FlatBufferModel> model;
    std::unique_ptr<tflite::Interpreter> interpreter;
    std::map<int, std::string> labels;
    
    std::map<int, std::string> loadLabels() {
        std::map<int, std::string> result;
        std::ifstream file(LABELS_PATH);
        std::string line;
        int index = 0;
        while (std::getline(file, line)) {
            result[index++] = line;
        }
        return result;
    }

public:
    ObjectDetector() {
        // Load model
        model = tflite::FlatBufferModel::BuildFromFile(MODEL_PATH);
        if (!model) {
            throw std::runtime_error("Failed to load model");
        }

        // Build interpreter
        tflite::ops::builtin::BuiltinOpResolver resolver;
        tflite::InterpreterBuilder(*model, resolver)(&interpreter);
        
        if (!interpreter) {
            throw std::runtime_error("Failed to construct interpreter");
        }

        // Allocate tensors
        if (interpreter->AllocateTensors() != kTfLiteOk) {
            throw std::runtime_error("Failed to allocate tensors");
        }

        // Load labels
        labels = loadLabels();
    }

    std::vector<Detection> detect(const cv::Mat& frame) {
        // Prepare input
        cv::Mat resized;
        cv::resize(frame, resized, cv::Size(INPUT_SIZE, INPUT_SIZE));
        
        // Convert to float and normalize
        cv::Mat float_mat;
        resized.convertTo(float_mat, CV_32F, 1.0/127.5, -1);

        // Copy to input tensor
        float* input = interpreter->typed_input_tensor<float>(0);
        std::memcpy(input, float_mat.data, float_mat.total() * float_mat.elemSize());

        // Run inference
        if (interpreter->Invoke() != kTfLiteOk) {
            throw std::runtime_error("Failed to invoke interpreter");
        }

        // Get output tensors
        float* boxes = interpreter->typed_output_tensor<float>(0);
        float* classes = interpreter->typed_output_tensor<float>(1);
        float* scores = interpreter->typed_output_tensor<float>(2);

        std::vector<Detection> detections;
        int num_detections = 20; // Adjust based on your model's output
        
        for (int i = 0; i < num_detections; i++) {
            if (scores[i] > 0.5f) { // Detection threshold
                Detection det;
                det.ymin = boxes[4*i] * frame.rows;
                det.xmin = boxes[4*i + 1] * frame.cols;
                det.ymax = boxes[4*i + 2] * frame.rows;
                det.xmax = boxes[4*i + 3] * frame.cols;
                det.confidence = scores[i];
                det.className = labels[static_cast<int>(classes[i])];
                detections.push_back(det);
            }
        }

        return detections;
    }
};

class DetectionApp {
private:
    Config config;
    ObjectDetector detector;
    crow::SimpleApp app;
    TemplateEngine templateEngine;
    std::unique_ptr<std::thread> detectionThread;
    bool running;

    std::vector<std::pair<int, std::string>> getAvailableCameras() {
        std::vector<std::pair<int, std::string>> cameras;
        for (int i = 0; i < 10; i++) {
            cv::VideoCapture cap(i);
            if (cap.isOpened()) {
                cv::Mat frame;
                if (cap.read(frame)) {
                    cameras.push_back({i, "Camera " + std::to_string(i)});
                }
                cap.release();
            }
        }
        return cameras;
    }

    void runDetection() {
        cv::VideoCapture cap(config.cameraIndex);
        if (!cap.isOpened()) {
            throw std::runtime_error("Failed to open camera");
        }

        // Setup UDP socket
        int sock = socket(AF_INET, SOCK_DGRAM, 0);
        struct sockaddr_in server_addr;
        server_addr.sin_family = AF_INET;
        server_addr.sin_port = htons(5806);
        inet_pton(AF_INET, config.ipOfRio.c_str(), &server_addr.sin_addr);

        while (running) {
            cv::Mat frame;
            if (!cap.read(frame)) {
                std::cerr << "Failed to capture frame" << std::endl;
                continue;
            }

            // Run detection
            auto detections = detector.detect(frame);

            // Convert detections to JSON and send via UDP
            if (!detections.empty()) {
                json j = json::array();
                for (const auto& det : detections) {
                    j.push_back(det.toJson());
                }
                std::string detection_str = j.dump();
                if (detection_str.length() <= 65507) { // UDP packet size limit
                    sendto(sock, detection_str.c_str(), detection_str.length(), 0,
                           (struct sockaddr*)&server_addr, sizeof(server_addr));
                }
            }

            // Convert frame to JPEG and emit via WebSocket
            std::vector<uchar> buf;
            cv::imencode(".jpg", frame, buf);
            // Note: WebSocket implementation would go here
        }

        close(sock);
        cap.release();
    }

public:
    DetectionApp() : config(Config::loadConfig()), running(false) {
        // Setup web routes with template rendering
        CROW_ROUTE(app, "/")(
            [this](){
                auto cameras = getAvailableCameras();
                
                json templateData;
                templateData["config"] = {
                    {"ipOfRio", config.ipOfRio},
                    {"camera_index", config.cameraIndex}
                };
                
                json cameraOptions = json::array();
                for (const auto& [idx, name] : cameras) {
                    cameraOptions.push_back(name);
                }
                templateData["available_cameras"] = cameraOptions;
                templateData["setting1_options"] = SETTING1_OPTIONS;

                return crow::response(200, templateEngine.render("config.html", templateData));
            }
        );

        CROW_ROUTE(app, "/update_config")
            .methods("POST"_method)
            ([this](const crow::request& req){
                auto params = crow::query_string(req.body);
                config.ipOfRio = params.get("ipOfRio");
                config.cameraIndex = std::stoi(params.get("camera_index"));
                Config::saveConfig(config);
                return crow::response(302, "/");  // Redirect back to home
            });

        // Setup WebSocket route for video streaming
        CROW_WEBSOCKET_ROUTE(app, "/ws")
            .onopen([](crow::websocket::connection& conn) {
                // Handle WebSocket connection opened
            })
            .onclose([](crow::websocket::connection& conn, const std::string& reason) {
                // Handle WebSocket connection closed
            })
            .onmessage([](crow::websocket::connection& conn, const std::string& data, bool is_binary) {
                // Handle incoming WebSocket messages
            });
    }

    void start() {
        running = true;
        detectionThread = std::make_unique<std::thread>(&DetectionApp::runDetection, this);
        app.port(5000).multithreaded().run();
    }

    void stop() {
        running = false;
        if (detectionThread && detectionThread->joinable()) {
            detectionThread->join();
        }
    }

    ~DetectionApp() {
        stop();
    }
};

int main() {
    try {
        DetectionApp app;
        app.start();
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }
    return 0;
}
//OLD
//
//
//
//
//
//
/*
#include <opencv2/opencv.hpp>
#include <tensorflow/lite/interpreter.h>
#include <tensorflow/lite/kernels/register.h>
#include <tensorflow/lite/model.h>
#include <tensorflow/lite/optional_debug_tools.h>
#include <crow.hpp>
#include <nlohmann/json.hpp>
#include <thread>
#include <fstream>
#include <sstream>
#include <map>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

using json = nlohmann::json;

// Constants
const char* CONFIG_FILE = "config.json";
const char* MODEL_PATH = "note_detect_v2-fp16_edgetpu.tflite";
const char* LABELS_PATH = "labels.txt";
const int INPUT_SIZE = 320;
const std::vector<std::string> SETTING1_OPTIONS = {"option1", "option2", "option3", "option4"};

// Configuration structure
struct Config {
    std::string ipOfRio;
    int cameraIndex;

    static Config loadConfig() {
        std::ifstream file(CONFIG_FILE);
        if (!file.is_open()) {
            // Create default config if file doesn't exist
            Config defaultConfig{"10.0.0.2", 0};
            saveConfig(defaultConfig);
            return defaultConfig;
        }
        json j;
        file >> j;
        return Config{j["ipOfRio"], j["camera_index"]};
    }

    static void saveConfig(const Config& config) {
        json j;
        j["ipOfRio"] = config.ipOfRio;
        j["camera_index"] = config.cameraIndex;
        std::ofstream file(CONFIG_FILE);
        file << j.dump(4);
    }
};

// Detection result structure
struct Detection {
    float xmin, ymin, xmax, ymax;
    float confidence;
    std::string className;

    json toJson() const {
        return {
            {"xmin", xmin},
            {"ymin", ymin},
            {"xmax", xmax},
            {"ymax", ymax},
            {"confidence", confidence},
            {"class", className}
        };
    }
};

class ObjectDetector {
private:
    std::unique_ptr<tflite::FlatBufferModel> model;
    std::unique_ptr<tflite::Interpreter> interpreter;
    std::map<int, std::string> labels;
    
    std::map<int, std::string> loadLabels() {
        std::map<int, std::string> result;
        std::ifstream file(LABELS_PATH);
        std::string line;
        int index = 0;
        while (std::getline(file, line)) {
            result[index++] = line;
        }
        return result;
    }

public:
    ObjectDetector() {
        // Load model
        model = tflite::FlatBufferModel::BuildFromFile(MODEL_PATH);
        if (!model) {
            throw std::runtime_error("Failed to load model");
        }

        // Build interpreter
        tflite::ops::builtin::BuiltinOpResolver resolver;
        tflite::InterpreterBuilder(*model, resolver)(&interpreter);
        
        if (!interpreter) {
            throw std::runtime_error("Failed to construct interpreter");
        }

        // Allocate tensors
        if (interpreter->AllocateTensors() != kTfLiteOk) {
            throw std::runtime_error("Failed to allocate tensors");
        }

        // Load labels
        labels = loadLabels();
    }

    std::vector<Detection> detect(const cv::Mat& frame) {
        // Prepare input
        cv::Mat resized;
        cv::resize(frame, resized, cv::Size(INPUT_SIZE, INPUT_SIZE));
        
        // Convert to float and normalize
        cv::Mat float_mat;
        resized.convertTo(float_mat, CV_32F, 1.0/127.5, -1);

        // Copy to input tensor
        float* input = interpreter->typed_input_tensor<float>(0);
        std::memcpy(input, float_mat.data, float_mat.total() * float_mat.elemSize());

        // Run inference
        if (interpreter->Invoke() != kTfLiteOk) {
            throw std::runtime_error("Failed to invoke interpreter");
        }

        // Get output tensors
        float* boxes = interpreter->typed_output_tensor<float>(0);
        float* classes = interpreter->typed_output_tensor<float>(1);
        float* scores = interpreter->typed_output_tensor<float>(2);

        std::vector<Detection> detections;
        int num_detections = 20; // Adjust based on your model's output
        
        for (int i = 0; i < num_detections; i++) {
            if (scores[i] > 0.5f) { // Detection threshold
                Detection det;
                det.ymin = boxes[4*i] * frame.rows;
                det.xmin = boxes[4*i + 1] * frame.cols;
                det.ymax = boxes[4*i + 2] * frame.rows;
                det.xmax = boxes[4*i + 3] * frame.cols;
                det.confidence = scores[i];
                det.className = labels[static_cast<int>(classes[i])];
                detections.push_back(det);
            }
        }

        return detections;
    }
};

class DetectionApp {
private:
    Config config;
    ObjectDetector detector;
    crow::SimpleApp app;
    std::unique_ptr<std::thread> detectionThread;
    bool running;

    void runDetection() {
        cv::VideoCapture cap(config.cameraIndex);
        if (!cap.isOpened()) {
            throw std::runtime_error("Failed to open camera");
        }

        // Setup UDP socket
        int sock = socket(AF_INET, SOCK_DGRAM, 0);
        struct sockaddr_in server_addr;
        server_addr.sin_family = AF_INET;
        server_addr.sin_port = htons(5806);
        inet_pton(AF_INET, config.ipOfRio.c_str(), &server_addr.sin_addr);

        while (running) {
            cv::Mat frame;
            if (!cap.read(frame)) {
                std::cerr << "Failed to capture frame" << std::endl;
                continue;
            }

            // Run detection
            auto detections = detector.detect(frame);

            // Convert detections to JSON and send via UDP
            if (!detections.empty()) {
                json j = json::array();
                for (const auto& det : detections) {
                    j.push_back(det.toJson());
                }
                std::string detection_str = j.dump();
                if (detection_str.length() <= 65507) { // UDP packet size limit
                    sendto(sock, detection_str.c_str(), detection_str.length(), 0,
                           (struct sockaddr*)&server_addr, sizeof(server_addr));
                }
            }

            // Convert frame to JPEG and emit via WebSocket
            std::vector<uchar> buf;
            cv::imencode(".jpg", frame, buf);
            // Note: WebSocket implementation would go here
        }

        close(sock);
        cap.release();
    }

public:
    DetectionApp() : config(Config::loadConfig()), running(false) {
        // Setup web routes
        CROW_ROUTE(app, "/")([](){
            return crow::response(200, "Detection Server Running");
        });

        CROW_ROUTE(app, "/config")
        .methods("GET"_method)
        ([this](){
            json j;
            j["ipOfRio"] = config.ipOfRio;
            j["camera_index"] = config.cameraIndex;
            return crow::response(200, j.dump());
        });

        CROW_ROUTE(app, "/config")
        .methods("POST"_method)
        ([this](const crow::request& req){
            auto j = json::parse(req.body);
            config.ipOfRio = j["ipOfRio"];
            config.cameraIndex = j["camera_index"];
            Config::saveConfig(config);
            return crow::response(200);
        });
    }

    void start() {
        running = true;
        detectionThread = std::make_unique<std::thread>(&DetectionApp::runDetection, this);
        app.port(5000).multithreaded().run();
    }

    void stop() {
        running = false;
        if (detectionThread && detectionThread->joinable()) {
            detectionThread->join();
        }
    }

    ~DetectionApp() {
        stop();
    }
};

int main() {
    try {
        DetectionApp app;
        app.start();
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }
    return 0;
}
*/