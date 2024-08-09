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
    final double IMAGE_WIDTH = 640; // Example image width in pixels
    final double IMAGE_HEIGHT = 480; // Example image height in pixels
    final double FOV_X = 60; // Horizontal field of view in degrees
    final double FOV_Y = 45; // Vertical field of view in degrees

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

                    // Normalize the coordinates
                    double normCenterX = (centerX / IMAGE_WIDTH) * 2 - 1;
                    double normCenterY = (centerY / IMAGE_HEIGHT) * 2 - 1;

                    // Calculate the rotation offsets
                    double offsetX = normCenterX;
                    double offsetY = normCenterY;

                    // Calculate rotation angles
                    double rotationAngleX = offsetX * FOV_X;
                    double rotationAngleY = offsetY * FOV_Y;

                    // Output the calculated rotation angles
                    System.out.println("Calculated Rotation Angles:");
                    System.out.println("Rotation Angle X: " + rotationAngleX + " degrees");
                    System.out.println("Rotation Angle Y: " + rotationAngleY + " degrees");

                    // Here you can send the rotation commands to the robot if needed
                } else {
                    System.out.println("Row " + i + " has an incorrect number of columns.");
                }
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
        System.out.println("error! " + e);
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
