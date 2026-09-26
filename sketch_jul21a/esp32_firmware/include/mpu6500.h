/**
 * mpu6500.h - MPU6500 六轴传感器驱动头文件
 *
 * MPU6500 是一款 6 轴惯性测量单元（IMU），包含：
 *   - 3 轴加速度计（ax, ay, az）：测量线性加速度
 *   - 3 轴陀螺仪（gx, gy, gz）：测量角速度
 *
 * 通信接口：I2C
 * I2C 地址：0x68（AD0 接地）或 0x69（AD0 接 VCC）
 */

#ifndef MPU6500_H
#define MPU6500_H

#include <Arduino.h>
#include <Wire.h>
#include "config.h"

// =============================================================================
// MPU6500 寄存器地址定义
// =============================================================================
#define MPU6500_REG_SELF_TEST_X_GYRO    0x00   // X 轴陀螺仪自检寄存器
#define MPU6500_REG_SELF_TEST_Y_GYRO    0x01   // Y 轴陀螺仪自检寄存器
#define MPU6500_REG_SELF_TEST_Z_GYRO    0x02   // Z 轴陀螺仪自检寄存器
#define MPU6500_REG_SELF_TEST_X_ACCEL   0x0D   // X 轴加速度计自检寄存器
#define MPU6500_REG_SELF_TEST_Y_ACCEL   0x0E   // Y 轴加速度计自检寄存器
#define MPU6500_REG_SELF_TEST_Z_ACCEL   0x0F   // Z 轴加速度计自检寄存器
#define MPU6500_REG_SMPLRT_DIV          0x19   // 采样率分频寄存器
#define MPU6500_REG_CONFIG              0x1A   // 配置寄存器
#define MPU6500_REG_GYRO_CONFIG         0x1B   // 陀螺仪配置寄存器
#define MPU6500_REG_ACCEL_CONFIG        0x1C   // 加速度计配置寄存器
#define MPU6500_REG_ACCEL_CONFIG_2      0x1D   // 加速度计配置寄存器 2
#define MPU6500_REG_FIFO_EN             0x23   // FIFO 使能寄存器
#define MPU6500_REG_INT_PIN_CFG         0x37   // 中断引脚配置寄存器
#define MPU6500_REG_INT_ENABLE          0x38   // 中断使能寄存器
#define MPU6500_REG_INT_STATUS          0x3A   // 中断状态寄存器
#define MPU6500_REG_ACCEL_XOUT_H        0x3B   // 加速度计 X 轴高位
#define MPU6500_REG_ACCEL_XOUT_L        0x3C   // 加速度计 X 轴低位
#define MPU6500_REG_ACCEL_YOUT_H        0x3D   // 加速度计 Y 轴高位
#define MPU6500_REG_ACCEL_YOUT_L        0x3E   // 加速度计 Y 轴低位
#define MPU6500_REG_ACCEL_ZOUT_H        0x3F   // 加速度计 Z 轴高位
#define MPU6500_REG_ACCEL_ZOUT_L        0x40   // 加速度计 Z 轴低位
#define MPU6500_REG_TEMP_OUT_H          0x41   // 温度高位
#define MPU6500_REG_TEMP_OUT_L          0x42   // 温度低位
#define MPU6500_REG_GYRO_XOUT_H         0x43   // 陀螺仪 X 轴高位
#define MPU6500_REG_GYRO_XOUT_L         0x44   // 陀螺仪 X 轴低位
#define MPU6500_REG_GYRO_YOUT_H         0x45   // 陀螺仪 Y 轴高位
#define MPU6500_REG_GYRO_YOUT_L         0x46   // 陀螺仪 Y 轴低位
#define MPU6500_REG_GYRO_ZOUT_H         0x47   // 陀螺仪 Z 轴高位
#define MPU6500_REG_GYRO_ZOUT_L         0x48   // 陀螺仪 Z 轴低位
#define MPU6500_REG_SIGNAL_PATH_RESET   0x68   // 信号路径复位寄存器
#define MPU6500_REG_USER_CTRL           0x6A   // 用户控制寄存器
#define MPU6500_REG_PWR_MGMT_1          0x6B   // 电源管理寄存器 1
#define MPU6500_REG_PWR_MGMT_2          0x6C   // 电源管理寄存器 2
#define MPU6500_REG_WHO_AM_I            0x75   // 设备 ID 寄存器

// MPU6500 WHO_AM_I 应返回的值
#define MPU6500_WHO_AM_I_VALUE          0x70

// =============================================================================
// 数据结构定义
// =============================================================================

/**
 * MPU6500 传感器数据结构
 * 包含原始数据和经过换算的物理量
 */
typedef struct {
    // 原始 ADC 值（16 位有符号整数）
    int16_t raw_ax, raw_ay, raw_az;    // 加速度原始值
    int16_t raw_gx, raw_gy, raw_gz;    // 陀螺仪原始值

    // 换算后的物理量
    float ax, ay, az;                  // 加速度（单位：g）
    float gx, gy, gz;                  // 角速度（单位：°/s）

    // 温度
    float temperature;                 // 芯片温度（单位：°C）

    // 时间戳
    uint32_t timestamp;                // 采集时刻（millis()）
} MPU6500_Data_t;

// =============================================================================
// MPU6500 驱动类
// =============================================================================

class MPU6500 {
public:
    /**
     * 构造函数
     * @param wire      TwoWire 引用（默认 Wire）
     * @param i2c_addr  I2C 地址（默认 0x68）
     */
    MPU6500(TwoWire &wire = Wire, uint8_t i2c_addr = MPU6500_I2C_ADDR);

    /**
     * 初始化 MPU6500 传感器
     * @param sda_pin   I2C SDA 引脚
     * @param scl_pin   I2C SCL 引脚
     * @param clk_speed I2C 时钟频率（Hz）
     * @return true=成功，false=失败
     */
    bool begin(int sda_pin = 21, int scl_pin = 22, uint32_t clk_speed = 400000);

    /**
     * 读取所有传感器数据
     * @param data 输出数据结构引用
     * @return true=成功，false=读取失败
     */
    bool readAll(MPU6500_Data_t &data);

    /**
     * 读取加速度计数据
     * @param ax, ay, az 输出加速度值（单位：g）
     * @return true=成功
     */
    bool readAccel(float &ax, float &ay, float &az);

    /**
     * 读取陀螺仪数据
     * @param gx, gy, gz 输出角速度值（单位：°/s）
     * @return true=成功
     */
    bool readGyro(float &gx, float &gy, float &gz);

    /**
     * 读取芯片温度
     * @return 温度值（°C）
     */
    float readTemperature();

    /**
     * 检查传感器是否在线
     * @return true=在线
     */
    bool isConnected();

    /**
     * 获取 WHO_AM_I 值
     * @return 设备 ID 值
     */
    uint8_t getDeviceID();

private:
    TwoWire &_wire;                    // I2C 总线引用
    uint8_t _addr;                     // I2C 设备地址
    float _accel_scale;                // 加速度计灵敏度（LSB/g）
    float _gyro_scale;                 // 陀螺仪灵敏度（LSB/°/s）
    bool _initialized;                 // 初始化标志

    // I2C 寄存器读写方法
    bool writeRegister(uint8_t reg, uint8_t value);
    bool readRegister(uint8_t reg, uint8_t &value);
    bool readRegisters(uint8_t reg, uint8_t *buffer, size_t length);
    int16_t readWord(uint8_t reg_high);
};

#endif // MPU6500_H
