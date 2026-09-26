/**
 * mpu6500.cpp - MPU6500 六轴传感器驱动实现
 *
 * 通过 I2C 总线与 MPU6500 通信，读取加速度计和陀螺仪数据
 * 支持数据校准和物理量换算
 */

#include "mpu6500.h"
#include "config.h"

// =============================================================================
// 构造函数
// =============================================================================
MPU6500::MPU6500(TwoWire &wire, uint8_t i2c_addr)
    : _wire(wire)
    , _addr(i2c_addr)
    , _accel_scale(1.0f)
    , _gyro_scale(1.0f)
    , _initialized(false)
{
}

// =============================================================================
// 初始化 MPU6500
// =============================================================================
bool MPU6500::begin(int sda_pin, int scl_pin, uint32_t clk_speed)
{
    DEBUG_PRINTLN("[MPU6500] 初始化传感器...");

    // 启动 I2C 通信
    _wire.begin(sda_pin, scl_pin, clk_speed);
    delay(100);  // 等待 I2C 总线稳定

    // 检测设备是否在线
    if (!isConnected()) {
        DEBUG_PRINTLN("[MPU6500] 错误：传感器未找到！");
        return false;
    }

    DEBUG_PRINTF("[MPU6500] 传感器已连接，WHO_AM_I = 0x%02X\n", getDeviceID());

    // 第一步：复位传感器
    // 设置 PWR_MGMT_1 的 bit7 (DEVICE_RESET) 为 1
    writeRegister(MPU6500_REG_PWR_MGMT_1, 0x80);
    delay(100);  // 等待复位完成

    // 等待复位完成（bit7 应自动清零）
    uint8_t pwr_mgmt;
    int retry = 0;
    do {
        delay(10);
        readRegister(MPU6500_REG_PWR_MGMT_1, pwr_mgmt);
        retry++;
    } while ((pwr_mgmt & 0x80) && retry < 50);

    if (retry >= 50) {
        DEBUG_PRINTLN("[MPU6500] 错误：复位超时！");
        return false;
    }

    // 第二步：唤醒传感器，选择时钟源
    // PWR_MGMT_1:
    //   bit6 = 0: 不睡眠（唤醒）
    //   bit5 = 0: 不循环模式
    //   bit4 = 0: 不禁用温度传感器
    //   bit2:0 = 001: 选择 X 轴陀螺仪 PLL 作为时钟源（最稳定）
    writeRegister(MPU6500_REG_PWR_MGMT_1, 0x01);
    delay(50);

    // 第三步：使能所有轴
    // PWR_MGMT_2: 所有轴都不待机
    writeRegister(MPU6500_REG_PWR_MGMT_2, 0x00);
    delay(10);

    // 第四步：配置数字低通滤波器（DLPF）
    // CONFIG 寄存器 (0x1A):
    //   bit2:0 = DLPF_CFG: 设置低通滤波器带宽
    //   MPU6500_DLPF_CFG = 3 → 加速度计 44Hz，陀螺仪 42Hz
    writeRegister(MPU6500_REG_CONFIG, MPU6500_DLPF_CFG & 0x07);
    delay(10);

    // 第五步：配置采样率分频
    // SMPLRT_DIV 寄存器: 采样率 = 陀螺仪输出率 / (1 + SMPLRT_DIV)
    // 当 DLPF_CFG = 0~6 时，陀螺仪输出率 = 8kHz
    // 100Hz 采样率: 8000 / (1 + 79) = 100
    // 实际使用 DLPF 时陀螺仪输出率为 1kHz
    // 100Hz 采样率: 1000 / (1 + 9) = 100
    uint8_t smplrt_div = (1000 / SAMPLE_RATE_HZ) - 1;
    writeRegister(MPU6500_REG_SMPLRT_DIV, smplrt_div);
    delay(10);
    DEBUG_PRINTF("[MPU6500] 采样率分频: %d (采样率 ≈ %d Hz)\n", smplrt_div, SAMPLE_RATE_HZ);

    // 第六步：配置加速度计量程
    // ACCEL_CONFIG 寄存器 (0x1C):
    //   bit4:3 = AFS_SEL: 选择加速度计量程
    //     00: ±2g,  灵敏度 16384 LSB/g
    //     01: ±4g,  灵敏度 8192  LSB/g
    //     10: ±8g,  灵敏度 4096  LSB/g
    //     11: ±16g, 灵敏度 2048  LSB/g
    uint8_t accel_cfg = 0;
    switch (MPU6500_ACCEL_RANGE) {
        case 2:   accel_cfg = 0x00; _accel_scale = 16384.0f; break;
        case 4:   accel_cfg = 0x08; _accel_scale = 8192.0f;  break;
        case 8:   accel_cfg = 0x10; _accel_scale = 4096.0f;  break;
        case 16:  accel_cfg = 0x18; _accel_scale = 2048.0f;  break;
        default:  accel_cfg = 0x18; _accel_scale = 2048.0f;  break;
    }
    writeRegister(MPU6500_REG_ACCEL_CONFIG, accel_cfg);
    delay(10);
    DEBUG_PRINTF("[MPU6500] 加速度计量程: ±%dg, 灵敏度: %.0f LSB/g\n",
                 MPU6500_ACCEL_RANGE, _accel_scale);

    // 第七步：配置加速度计 DLPF
    // ACCEL_CONFIG_2 (0x1D):
    //   bit3:0 = A_DLPF_CFG: 加速度计低通滤波器配置
    //   设置为 3 对应 44Hz 带宽
    writeRegister(MPU6500_REG_ACCEL_CONFIG_2, MPU6500_DLPF_CFG & 0x0F);
    delay(10);

    // 第八步：配置陀螺仪量程
    // GYRO_CONFIG 寄存器 (0x1B):
    //   bit4:3 = GFS_SEL: 选择陀螺仪量程
    //     00: ±250°/s,  灵敏度 131  LSB/(°/s)
    //     01: ±500°/s,  灵敏度 65.5 LSB/(°/s)
    //     10: ±1000°/s, 灵敏度 32.8 LSB/(°/s)
    //     11: ±2000°/s, 灵敏度 16.4 LSB/(°/s)
    uint8_t gyro_cfg = 0;
    switch (MPU6500_GYRO_RANGE) {
        case 250:   gyro_cfg = 0x00; _gyro_scale = 131.0f;  break;
        case 500:   gyro_cfg = 0x08; _gyro_scale = 65.5f;   break;
        case 1000:  gyro_cfg = 0x10; _gyro_scale = 32.8f;   break;
        case 2000:  gyro_cfg = 0x18; _gyro_scale = 16.4f;   break;
        default:    gyro_cfg = 0x18; _gyro_scale = 16.4f;   break;
    }
    writeRegister(MPU6500_REG_GYRO_CONFIG, gyro_cfg);
    delay(10);
    DEBUG_PRINTF("[MPU6500] 陀螺仪量程: ±%d°/s, 灵敏度: %.1f LSB/(°/s)\n",
                 MPU6500_GYRO_RANGE, _gyro_scale);

    // 第九步：配置中断引脚
    // INT_PIN_CFG (0x37):
    //   bit1 = INT_LEVEL: 0=高电平有效
    //   bit2 = INT_OPEN:  0=推挽输出
    //   bit3 = LATCH_INT_EN: 0=脉冲模式（50us）
    //   bit4 = INT_RD_CLEAR: 0=读取 INT_STATUS 清除中断
    writeRegister(MPU6500_REG_INT_PIN_CFG, 0x10);  // INT_RD_CLEAR = 1
    delay(10);

    // 使能数据就绪中断
    // INT_ENABLE (0x38):
    //   bit0 = DATA_RDY_EN: 1=使能数据就绪中断
    writeRegister(MPU6500_REG_INT_ENABLE, 0x01);
    delay(10);

    // 读取 INT_STATUS 清除可能存在的中断标志
    uint8_t int_status;
    readRegister(MPU6500_REG_INT_STATUS, int_status);

    _initialized = true;
    DEBUG_PRINTLN("[MPU6500] 传感器初始化成功！");

    return true;
}

// =============================================================================
// 读取所有传感器数据
// =============================================================================
bool MPU6500::readAll(MPU6500_Data_t &data)
{
    if (!_initialized) {
        return false;
    }

    // 一次性读取 14 字节数据（加速度 + 温度 + 陀螺仪）
    // 寄存器地址 0x3B ~ 0x48，连续排列：
    //   0x3B-0x40: ACCEL_XOUT (H/L), ACCEL_YOUT (H/L), ACCEL_ZOUT (H/L)
    //   0x41-0x42: TEMP_OUT (H/L)
    //   0x43-0x48: GYRO_XOUT (H/L), GYRO_YOUT (H/L), GYRO_ZOUT (H/L)
    uint8_t buffer[14];
    if (!readRegisters(MPU6500_REG_ACCEL_XOUT_H, buffer, 14)) {
        DEBUG_PRINTLN("[MPU6500] 错误：数据读取失败！");
        return false;
    }

    // 解析原始数据（大端序，高字节在前）
    data.raw_ax = (int16_t)((buffer[0] << 8) | buffer[1]);
    data.raw_ay = (int16_t)((buffer[2] << 8) | buffer[3]);
    data.raw_az = (int16_t)((buffer[4] << 8) | buffer[5]);

    // 解析温度原始值
    int16_t raw_temp = (int16_t)((buffer[6] << 8) | buffer[7]);

    data.raw_gx = (int16_t)((buffer[8]  << 8) | buffer[9]);
    data.raw_gy = (int16_t)((buffer[10] << 8) | buffer[11]);
    data.raw_gz = (int16_t)((buffer[12] << 8) | buffer[13]);

    // 换算为物理量
    // 加速度：原始值 / 灵敏度 = g
    data.ax = (float)data.raw_ax / _accel_scale;
    data.ay = (float)data.raw_ay / _accel_scale;
    data.az = (float)data.raw_az / _accel_scale;

    // 陀螺仪：原始值 / 灵敏度 = °/s
    data.gx = (float)data.raw_gx / _gyro_scale;
    data.gy = (float)data.raw_gy / _gyro_scale;
    data.gz = (float)data.raw_gz / _gyro_scale;

    // 温度：根据数据手册公式换算
    // Temperature = (TEMP_OUT / 333.87) + 21.0
    data.temperature = (float)raw_temp / 333.87f + 21.0f;

    // 记录采集时间戳
    data.timestamp = millis();

    return true;
}

// =============================================================================
// 读取加速度计数据
// =============================================================================
bool MPU6500::readAccel(float &ax, float &ay, float &az)
{
    if (!_initialized) return false;

    uint8_t buffer[6];
    if (!readRegisters(MPU6500_REG_ACCEL_XOUT_H, buffer, 6)) {
        return false;
    }

    int16_t raw_x = (int16_t)((buffer[0] << 8) | buffer[1]);
    int16_t raw_y = (int16_t)((buffer[2] << 8) | buffer[3]);
    int16_t raw_z = (int16_t)((buffer[4] << 8) | buffer[5]);

    ax = (float)raw_x / _accel_scale;
    ay = (float)raw_y / _accel_scale;
    az = (float)raw_z / _accel_scale;

    return true;
}

// =============================================================================
// 读取陀螺仪数据
// =============================================================================
bool MPU6500::readGyro(float &gx, float &gy, float &gz)
{
    if (!_initialized) return false;

    uint8_t buffer[6];
    if (!readRegisters(MPU6500_REG_GYRO_XOUT_H, buffer, 6)) {
        return false;
    }

    int16_t raw_x = (int16_t)((buffer[0] << 8) | buffer[1]);
    int16_t raw_y = (int16_t)((buffer[2] << 8) | buffer[3]);
    int16_t raw_z = (int16_t)((buffer[4] << 8) | buffer[5]);

    gx = (float)raw_x / _gyro_scale;
    gy = (float)raw_y / _gyro_scale;
    gz = (float)raw_z / _gyro_scale;

    return true;
}

// =============================================================================
// 读取温度
// =============================================================================
float MPU6500::readTemperature()
{
    if (!_initialized) return 0.0f;

    int16_t raw_temp = readWord(MPU6500_REG_TEMP_OUT_H);
    return (float)raw_temp / 333.87f + 21.0f;
}

// =============================================================================
// 检查传感器是否在线
// =============================================================================
bool MPU6500::isConnected()
{
    return getDeviceID() == MPU6500_WHO_AM_I_VALUE;
}

// =============================================================================
// 获取设备 ID
// =============================================================================
uint8_t MPU6500::getDeviceID()
{
    uint8_t id = 0;
    readRegister(MPU6500_REG_WHO_AM_I, id);
    return id;
}

// =============================================================================
// I2C 底层通信方法
// =============================================================================

/**
 * 写入单个寄存器
 * @param reg   寄存器地址
 * @param value 写入值
 * @return true=成功
 */
bool MPU6500::writeRegister(uint8_t reg, uint8_t value)
{
    _wire.beginTransmission(_addr);
    _wire.write(reg);
    _wire.write(value);
    uint8_t error = _wire.endTransmission();

    if (error != 0) {
        DEBUG_PRINTF("[MPU6500] I2C 写入错误: reg=0x%02X, error=%d\n", reg, error);
        return false;
    }
    return true;
}

/**
 * 读取单个寄存器
 * @param reg    寄存器地址
 * @param value  输出读取值
 * @return true=成功
 */
bool MPU6500::readRegister(uint8_t reg, uint8_t &value)
{
    _wire.beginTransmission(_addr);
    _wire.write(reg);
    uint8_t error = _wire.endTransmission(false);  // 不发送停止位（重复开始）

    if (error != 0) {
        DEBUG_PRINTF("[MPU6500] I2C 传输错误: reg=0x%02X, error=%d\n", reg, error);
        return false;
    }

    uint8_t count = _wire.requestFrom(_addr, (uint8_t)1, (uint8_t)true);
    if (count != 1) {
        DEBUG_PRINTF("[MPU6500] I2C 读取失败: reg=0x%02X, count=%d\n", reg, count);
        return false;
    }

    value = _wire.read();
    return true;
}

/**
 * 连续读取多个寄存器
 * @param reg    起始寄存器地址
 * @param buffer 输出缓冲区
 * @param length 读取字节数
 * @return true=成功
 */
bool MPU6500::readRegisters(uint8_t reg, uint8_t *buffer, size_t length)
{
    _wire.beginTransmission(_addr);
    _wire.write(reg);
    uint8_t error = _wire.endTransmission(false);

    if (error != 0) {
        DEBUG_PRINTF("[MPU6500] I2C 传输错误: reg=0x%02X, len=%d, error=%d\n",
                     reg, length, error);
        return false;
    }

    uint8_t count = _wire.requestFrom(_addr, (uint8_t)length, (uint8_t)true);
    if (count != length) {
        DEBUG_PRINTF("[MPU6500] I2C 读取字节不匹配: 期望=%d, 实际=%d\n", length, count);
        return false;
    }

    for (size_t i = 0; i < length; i++) {
        buffer[i] = _wire.read();
    }

    return true;
}

/**
 * 读取 16 位寄存器值（连续两个字节）
 * @param reg_high 高字节寄存器地址
 * @return 16 位有符号值
 */
int16_t MPU6500::readWord(uint8_t reg_high)
{
    uint8_t buffer[2];
    readRegisters(reg_high, buffer, 2);
    return (int16_t)((buffer[0] << 8) | buffer[1]);
}
