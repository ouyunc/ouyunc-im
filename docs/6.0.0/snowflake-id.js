var bigInt = require("./big-integer");
/**
 * js 雪花算法
 * @type {Snowflake}
 */
const Snowflake = /** @class */ (function() {
	function Snowflake(_workerId, _dataCenterId, _sequence) {
		this.twepoch = bigInt('1288834974657');
		//this.twepoch = 0n;
		this.workerIdBits = 5;
		this.dataCenterIdBits = 5;
		this.maxWrokerId = -1 ^ (-1 << this.workerIdBits); // 值为：31
		this.maxDataCenterId = -1 ^ (-1 << this.dataCenterIdBits); // 值为：31
		this.sequenceBits = 12;
		this.workerIdShift = this.sequenceBits; // 值为：12
		this.dataCenterIdShift = this.sequenceBits + this.workerIdBits; // 值为：17
		this.timestampLeftShift = this.sequenceBits + this.workerIdBits + this.dataCenterIdBits; // 值为：22
		this.sequenceMask = -1 ^ (-1 << this.sequenceBits); // 值为：4095
		this.lastTimestamp = -1;
		this.workerId = _workerId;
		this.dataCenterId = _dataCenterId;
		this.sequence = _sequence;
		//设置默认值,从环境变量取
		if (this.workerId > this.maxWrokerId || this.workerId< 0) {
			throw new Error('_workerId must max than 0 and small than maxWrokerId-[' + this.maxWrokerId + ']');
		}
		if (this.dataCenterId > this.maxDataCenterId || this.dataCenterId < 0) {
			throw new Error('_dataCenterId must max than 0 and small than maxDataCenterId-[' + this
				.maxDataCenterId + ']');
		}

	}

	Snowflake.prototype.tilNextMillis = function(lastTimestamp) {
		let timestamp = this.timeGen();
		while (timestamp <= lastTimestamp) {
			timestamp = this.timeGen();
		}
		return timestamp;
	};
	Snowflake.prototype.timeGen = function() {
		return Date.now();
	};
	Snowflake.prototype.nextBinaryIdStr = function() {
		let timestamp = this.timeGen();
		if (timestamp < this.lastTimestamp) {
			throw new Error('Clock moved backwards. Refusing to generate id for ' +
				(this.lastTimestamp - timestamp));
		}
		if (this.lastTimestamp === timestamp) {
			this.sequence = (this.sequence + 1) & this.sequenceMask;
			if (this.sequence === 0) {
				timestamp = this.tilNextMillis(this.lastTimestamp);
			}
		} else {
			this.sequence = 0;
		}
		this.lastTimestamp = timestamp;
		return bigInt(String(timestamp - this.twepoch)).shiftLeft(this.timestampLeftShift).or(bigInt(this.dataCenterId).shiftLeft(this.dataCenterIdShift)).or(bigInt(this.workerId).shiftLeft(this.dataCenterIdShift)).or(this.sequence).toString(2).padStart(64, '0');
		// return ((timestamp - this.twepoch) << this.timestampLeftShift) |
		// 	(this.dataCenterId << this.dataCenterIdShift) |
		// 	(this.workerId << this.workerIdShift) |
		// 	this.sequence;
	};
	return Snowflake;
}());
export {
	bigInt,
	Snowflake
}