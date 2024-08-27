// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;

/**
 * The VM is configured to automatically run this class, and to call the functions corresponding to
 * each mode, as described in the TimedRobot documentation. If you change the name of this class or
 * the package after creating this project, you must also update the build.gradle file in the
 * project.
 */
public class Robot extends TimedRobot {
  private Command m_autonomousCommand;

  private RobotContainer m_robotContainer;

  /**
   * This function is run when the robot is first started up and should be used for any
   * initialization code.
   */
  @Override
  public void robotInit() {
    // Instantiate our RobotContainer.  This will perform all our button bindings, and put our
    // autonomous chooser on the dashboard.
    m_robotContainer = new RobotContainer();
  }

  /**
   * This function is called every 20 ms, no matter the mode. Use this for items like diagnostics
   * that you want ran during disabled, autonomous, teleoperated and test.
   *
   * <p>This runs after the mode specific periodic functions, but before LiveWindow and
   * SmartDashboard integrated updating.
   */
  @Override
  public void robotPeriodic() {
    // Runs the Scheduler.  This is responsible for polling buttons, adding newly-scheduled
    // commands, running already-scheduled commands, removing finished or interrupted commands,
    // and running subsystem periodic() methods.  This must be called from the robot's periodic
    // block in order for anything in the Command-based framework to work.
    CommandScheduler.getInstance().run();
  }

  /** This function is called once each time the robot enters Disabled mode. */
  @Override
  public void disabledInit() {}

  @Override
  public void disabledPeriodic() {}

  /** This autonomous runs the autonomous command selected by your {@link RobotContainer} class. */
  @Override
  public void autonomousInit() {
    m_autonomousCommand = m_robotContainer.getAutonomousCommand();

    // schedule the autonomous command (example)
    if (m_autonomousCommand != null) {
      m_autonomousCommand.schedule();
    }
  }

  /** This function is called periodically during autonomous. */
  @Override
  public void autonomousPeriodic() {
    
  }

  @Override
  public void teleopInit() {
    // This makes sure that the autonomous stops running when
    // teleop starts running. If you want the autonomous to
    // continue until interrupted by another command, remove
    // this line or comment it out.
    if (m_autonomousCommand != null) {
      m_autonomousCommand.cancel();
    }
        final double IMAGE_WIDTH = 640.0; // Example image width in pixels
        final double IMAGE_HEIGHT = 480.0; // Example image height in pixels
        final double FOV_X = Math.toRadians(60.0); // Horizontal field of view in radians
        final double FOV_Y = Math.toRadians(45.0); // Vertical field of view in radians

        // Camera coordinates
        final double CAMERA_X = 8.0;
        final double CAMERA_Y = 10.5;
        final double CAMERA_Z = 24.0;

        int port = 5806;
        byte[] buffer = new byte[65507];

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UDP server up and listening on port " + port);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("Received detections from client:");
                System.out.println(received);

                // Parse the received string into individual rows and columns
                String[] rows = received.split("\n");
                String header = rows[0];
                String[] columns = header.split(",");

                for (int i = 1; i < rows.length; i++) {
                    String[] values = rows[i].split(",");

                    if (values.length == columns.length) {
                        // Assuming the data format is known and consistent
                        double xmin = Double.parseDouble(values[0]);
                        double ymin = Double.parseDouble(values[1]);
                        double xmax = Double.parseDouble(values[2]);
                        double ymax = Double.parseDouble(values[3]);
                        double confidence = Double.parseDouble(values[4]);
                        int cls = Integer.parseInt(values[5]);
                        String name = values[6];

                        // Print the values
                        System.out.println("Row " + i + ":");
                        System.out.println("xmin: " + xmin);
                        System.out.println("ymin: " + ymin);
                        System.out.println("xmax: " + xmax);
                        System.out.println("ymax: " + ymax);
                        System.out.println("confidence: " + confidence);
                        System.out.println("class: " + cls);
                        System.out.println("name: " + name);

                        // Calculate the center of the bounding box
                        double centerX = (xmin + xmax) / 2.0;
                        double centerY = (ymin + ymax) / 2.0;

                        // Normalize the coordinates to [-1, 1] range
                        double normCenterX = (centerX / IMAGE_WIDTH) * 2 - 1;
                        double normCenterY = (centerY / IMAGE_HEIGHT) * 2 - 1;

                        // Calculate the direction vector from the camera to the object in 3D space
                        double directionX = Math.tan(normCenterX * FOV_X / 2.0);
                        double directionY = Math.tan(normCenterY * FOV_Y / 2.0);
                        double directionZ = 1.0; // Assuming the camera is looking straight along the Z-axis

                        // Apply the camera's position
                        double objectX = CAMERA_X + directionX;
                        double objectY = CAMERA_Y + directionY;
                        double objectZ = CAMERA_Z + directionZ;

                        // Calculate the vector from the camera to the object
                        double relativeX = objectX - CAMERA_X;
                        double relativeY = objectY - CAMERA_Y;
                        double relativeZ = objectZ - CAMERA_Z;

                        // Calculate the magnitude of the relative vector
                        double magnitude = Math.sqrt(relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ);

                        // Normalize the relative vector
                        relativeX /= magnitude;
                        relativeY /= magnitude;
                        relativeZ /= magnitude;

                        // Calculate the rotation angle in radians (angle with the Z-axis)
                        double rotationAngle = Math.acos(relativeZ);

                        // Output the calculated rotation angle in radians
                        System.out.println("Calculated Rotation Angle (in radians): " + rotationAngle);

                        // Here you can send the rotation commands to the robot if needed
                    } else {
                        System.out.println("Row " + i + " has an incorrect number of columns.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error! " + e);
        }
    }


  /** This function is called periodically during operator control. */
  @Override
  public void teleopPeriodic() {}

  @Override
  public void testInit() {
    // Cancels all running commands at the start of test mode.
    CommandScheduler.getInstance().cancelAll();
  }

  /** This function is called periodically during test mode. */
  @Override
  public void testPeriodic() {}

  /** This function is called once when the robot is first started up. */
  @Override
  public void simulationInit() {}

  /** This function is called periodically whilst in simulation. */
  @Override
  public void simulationPeriodic() {}
}
