/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
 * Copyright (C) 2026 Benjamin Vernoux
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.source.tuner.hydrasdr;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.source.tuner.TunerType;
import io.github.dsheirer.source.tuner.configuration.TunerConfiguration;

/**
 * Configuration for HydraSDR native tuner.
 *
 * Uses the unified gain API from libhydrasdr - individual gain type values
 * are stored directly rather than preset enums.
 */
public class HydraSdrTunerConfiguration extends TunerConfiguration
{
	private int mSampleRate = HydraSdrTunerController.DEFAULT_SAMPLE_RATE;
	private int mLnaGain = HydraSdrTunerController.LNA_GAIN_DEFAULT;
	private int mRfGain = -1;
	private int mMixerGain = HydraSdrTunerController.MIXER_GAIN_DEFAULT;
	private int mFilterGain = -1;
	private int mVgaGain = HydraSdrTunerController.VGA_GAIN_DEFAULT;
	private int mLinearityGain = 14;
	private int mSensitivityGain = 0;
	private boolean mLnaAgc = false;
	private boolean mRfAgc = false;
	private boolean mMixerAgc = false;
	private boolean mFilterAgc = false;
	private boolean mBiasT = false;
	private int mGainMode = 0; /* 0=linearity, 1=sensitivity, 2=custom */

	/**
	 * Default constructor for JAXB
	 */
	public HydraSdrTunerConfiguration()
	{
		super(HydraSdrTunerController.FALLBACK_MIN_FREQUENCY_HZ,
			HydraSdrTunerController.FALLBACK_MAX_FREQUENCY_HZ);
	}

	@Override
	@JacksonXmlProperty(isAttribute = true, localName = "type",
		namespace = "http://www.w3.org/2001/XMLSchema-instance")
	public TunerType getTunerType()
	{
		return TunerType.HYDRASDR;
	}

	public HydraSdrTunerConfiguration(String uniqueID)
	{
		super(uniqueID);
	}

	@JacksonXmlProperty(isAttribute = true, localName = "sample_rate")
	public int getSampleRate()
	{
		return mSampleRate;
	}

	public void setSampleRate(int sampleRate)
	{
		mSampleRate = sampleRate;
	}

	/**
	 * Migrates the legacy preset gain enum (for example LINEARITY_14 or SENSITIVITY_10).
	 */
	@JsonProperty("gain")
	@JacksonXmlProperty(isAttribute = true, localName = "gain")
	public void setLegacyGain(String gain)
	{
		if(gain == null)
		{
			return;
		}

		if("CUSTOM".equals(gain))
		{
			mGainMode = 2;
			return;
		}

		int separator = gain.lastIndexOf('_');
		if(separator < 0 || separator == gain.length() - 1)
		{
			return;
		}

		try
		{
			int value = Integer.parseInt(gain.substring(separator + 1));
			if(gain.startsWith("LINEARITY_"))
			{
				mGainMode = 0;
				mLinearityGain = value;
			}
			else if(gain.startsWith("SENSITIVITY_"))
			{
				mGainMode = 1;
				mSensitivityGain = value;
			}
		}
		catch(NumberFormatException e)
		{
			// Ignore invalid legacy values and retain the current defaults.
		}
	}

	/**
	 * Migrates the legacy IF gain into the unified VGA gain value.
	 */
	@JsonProperty("IFGain")
	@JsonAlias("if_gain")
	@JacksonXmlProperty(isAttribute = true, localName = "if_gain")
	public void setLegacyIfGain(int gain)
	{
		mVgaGain = gain;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "lna_gain")
	public int getLnaGain()
	{
		return mLnaGain;
	}

	@JsonAlias("LNAGain")
	public void setLnaGain(int gain)
	{
		mLnaGain = gain;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "rf_gain")
	public int getRfGain()
	{
		return mRfGain;
	}

	public void setRfGain(int gain)
	{
		mRfGain = gain;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "mixer_gain")
	public int getMixerGain()
	{
		return mMixerGain;
	}

	public void setMixerGain(int gain)
	{
		mMixerGain = gain;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "filter_gain")
	public int getFilterGain()
	{
		return mFilterGain;
	}

	public void setFilterGain(int gain)
	{
		mFilterGain = gain;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "vga_gain")
	public int getVgaGain()
	{
		return mVgaGain;
	}

	public void setVgaGain(int gain)
	{
		mVgaGain = gain;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "linearity_gain")
	public int getLinearityGain()
	{
		return mLinearityGain;
	}

	public void setLinearityGain(int gain)
	{
		mLinearityGain = gain;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "sensitivity_gain")
	public int getSensitivityGain()
	{
		return mSensitivityGain;
	}

	public void setSensitivityGain(int gain)
	{
		mSensitivityGain = gain;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "lna_agc")
	public boolean isLnaAgc()
	{
		return mLnaAgc;
	}

	@JsonAlias("LNAAGC")
	public void setLnaAgc(boolean enabled)
	{
		mLnaAgc = enabled;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "rf_agc")
	public boolean isRfAgc()
	{
		return mRfAgc;
	}

	public void setRfAgc(boolean enabled)
	{
		mRfAgc = enabled;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "mixer_agc")
	public boolean isMixerAgc()
	{
		return mMixerAgc;
	}

	@JsonAlias("mixerAGC")
	public void setMixerAgc(boolean enabled)
	{
		mMixerAgc = enabled;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "filter_agc")
	public boolean isFilterAgc()
	{
		return mFilterAgc;
	}

	public void setFilterAgc(boolean enabled)
	{
		mFilterAgc = enabled;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "bias_t")
	public boolean isBiasT()
	{
		return mBiasT;
	}

	public void setBiasT(boolean enabled)
	{
		mBiasT = enabled;
	}

	@JacksonXmlProperty(isAttribute = true, localName = "gain_mode")
	public int getGainMode()
	{
		return mGainMode;
	}

	public void setGainMode(int gainMode)
	{
		mGainMode = gainMode;
	}
}
