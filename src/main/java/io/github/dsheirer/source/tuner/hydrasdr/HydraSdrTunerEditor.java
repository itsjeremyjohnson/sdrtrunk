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

import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.source.SourceException;
import io.github.dsheirer.source.tuner.manager.DiscoveredTuner;
import io.github.dsheirer.source.tuner.manager.TunerManager;
import io.github.dsheirer.source.tuner.manager.TunerStatus;
import io.github.dsheirer.source.tuner.ui.TunerEditor;
import java.util.List;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.SpinnerNumberModel;

/**
 * HydraSDR tuner editor using native libhydrasdr API.
 *
 * Dynamically builds gain controls based on device capability flags.
 */
public class HydraSdrTunerEditor extends TunerEditor<HydraSdrTuner, HydraSdrTunerConfiguration>
{
	private static final long serialVersionUID = 1L;
	private static final Logger mLog = LoggerFactory.getLogger(HydraSdrTunerEditor.class);

	private static final String[] GAIN_MODES = {"Linearity", "Sensitivity", "Custom"};

	private JButton mTunerInfoButton;
	private JComboBox<HydraSdrSampleRate> mSampleRateCombo;
	private JComboBox<String> mGainModeCombo;
	private JSlider mMasterGainSlider;
	private JLabel mMasterGainLabel;
	private JLabel mMasterGainValueLabel;
	private JSlider mVgaGainSlider;
	private JLabel mVgaGainLabel;
	private JLabel mVgaGainValueLabel;
	private JSlider mLnaGainSlider;
	private JLabel mLnaGainValueLabel;
	private JSlider mRfGainSlider;
	private JLabel mRfGainValueLabel;
	private JSlider mMixerGainSlider;
	private JLabel mMixerGainValueLabel;
	private JSlider mFilterGainSlider;
	private JLabel mFilterGainValueLabel;
	private JCheckBox mLnaAgcCheckBox;
	private JCheckBox mRfAgcCheckBox;
	private JCheckBox mMixerAgcCheckBox;
	private JCheckBox mFilterAgcCheckBox;
	private JCheckBox mBiasTCheckBox;

	public HydraSdrTunerEditor(UserPreferences userPreferences, TunerManager tunerManager,
		DiscoveredTuner discoveredTuner)
	{
		super(userPreferences, tunerManager, discoveredTuner);
		init();
		tunerStatusUpdated();
	}

	@Override
	public long getMinimumTunableFrequency()
	{
		if(hasTuner())
		{
			HydraSdrDeviceInfo info = getTuner().getController().getDeviceInfo();
			if(info != null && info.getMinFrequency() > 0)
			{
				return info.getMinFrequency();
			}
		}
		return HydraSdrTunerController.FALLBACK_MIN_FREQUENCY_HZ;
	}

	@Override
	public long getMaximumTunableFrequency()
	{
		if(hasTuner())
		{
			HydraSdrDeviceInfo info = getTuner().getController().getDeviceInfo();
			if(info != null && info.getMaxFrequency() > 0)
			{
				return info.getMaxFrequency();
			}
		}
		return HydraSdrTunerController.FALLBACK_MAX_FREQUENCY_HZ;
	}

	@Override
	protected void tunerStatusUpdated()
	{
		setLoading(true);

		if(hasTuner())
		{
			getTunerIdLabel().setText(getTuner().getPreferredName());
		}
		else
		{
			getTunerIdLabel().setText(getDiscoveredTuner().getId());
		}

		String status = getDiscoveredTuner().getTunerStatus().toString();
		if(getDiscoveredTuner().hasErrorMessage())
		{
			status += " - " + getDiscoveredTuner().getErrorMessage();
		}
		getTunerStatusLabel().setText(status);
		getButtonPanel().updateControls();
		getFrequencyPanel().updateControls();
		getSampleRateCombo().setEnabled(hasTuner() && !getTuner().getTunerController().isLockedSampleRate());
		getTunerInfoButton().setEnabled(hasTuner());
		getBiasTCheckBox().setEnabled(hasTuner() &&
			getTuner().getController().hasCapability(HydraSdrNative.CAP_BIAS_TEE));

		if(hasTuner() && hasConfiguration())
		{
			getBiasTCheckBox().setSelected(getConfiguration().isBiasT());
		}

		updateGainControls();

		if(hasTuner())
		{
			List<HydraSdrSampleRate> rates = getTuner().getController().getSampleRates();
			getSampleRateCombo().setModel(new DefaultComboBoxModel<>(
				rates.toArray(new HydraSdrSampleRate[0])));

			if(hasConfiguration())
			{
				HydraSdrSampleRate sampleRate = getTuner().getController()
					.getSampleRate(getConfiguration().getSampleRate());
				getSampleRateCombo().setSelectedItem(sampleRate);
			}
		}
		else
		{
			getSampleRateCombo().setModel(new DefaultComboBoxModel<>());
		}

		setLoading(false);
	}

	private void init()
	{
		setLayout(new MigLayout("fill,wrap 3", "[right][grow,fill][fill]",
			"[][][][][][][][][][][][][][][][grow]"));

		add(new JLabel("Tuner:"));
		add(getTunerIdLabel());
		add(getTunerInfoButton());

		add(new JLabel("Status:"));
		add(getTunerStatusLabel(), "wrap");

		add(getButtonPanel(), "span,align left");

		add(new JSeparator(), "span,growx,push");

		add(new JLabel("Frequency (MHz):"));
		add(getFrequencyPanel(), "wrap");

		add(new JLabel("Sample Rate:"));
		add(getSampleRateCombo(), "wrap");

		add(new JLabel());
		add(getBiasTCheckBox(), "wrap");

		add(new JSeparator(), "span,growx,push");
		add(new JLabel("Gain Control"), "wrap");

		add(new JLabel("Mode:"));
		add(getGainModeCombo(), "wrap");

		add(getMasterGainLabel());
		add(getMasterGainSlider());
		add(getMasterGainValueLabel());

		add(getVgaGainLabel());
		add(getVgaGainSlider());
		add(getVgaGainValueLabel());

		add(getFilterAgcCheckBox());
		add(getFilterGainSlider());
		add(getFilterGainValueLabel());

		add(getMixerAgcCheckBox());
		add(getMixerGainSlider());
		add(getMixerGainValueLabel());

		add(getRfAgcCheckBox());
		add(getRfGainSlider());
		add(getRfGainValueLabel());

		add(getLnaAgcCheckBox());
		add(getLnaGainSlider());
		add(getLnaGainValueLabel());
	}

	private JCheckBox getLnaAgcCheckBox()
	{
		if(mLnaAgcCheckBox == null)
		{
			mLnaAgcCheckBox = new JCheckBox("AGC LNA:");
			mLnaAgcCheckBox.setEnabled(false);
			mLnaAgcCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_LNA_AGC,
							mLnaAgcCheckBox.isSelected() ? 1 : 0);
						getLnaGainSlider().setEnabled(!mLnaAgcCheckBox.isSelected() &&
							getTuner().getController().hasCapability(HydraSdrNative.CAP_LNA_GAIN));
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting LNA AGC", e1);
					}
				}
			});
		}
		return mLnaAgcCheckBox;
	}

	private JCheckBox getRfAgcCheckBox()
	{
		if(mRfAgcCheckBox == null)
		{
			mRfAgcCheckBox = new JCheckBox("AGC RF:");
			mRfAgcCheckBox.setEnabled(false);
			mRfAgcCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_RF_AGC,
							mRfAgcCheckBox.isSelected() ? 1 : 0);
						getRfGainSlider().setEnabled(!mRfAgcCheckBox.isSelected() &&
							getTuner().getController().hasCapability(HydraSdrNative.CAP_RF_GAIN));
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting RF AGC", e1);
					}
				}
			});
		}
		return mRfAgcCheckBox;
	}

	private JCheckBox getMixerAgcCheckBox()
	{
		if(mMixerAgcCheckBox == null)
		{
			mMixerAgcCheckBox = new JCheckBox("AGC Mixer:");
			mMixerAgcCheckBox.setEnabled(false);
			mMixerAgcCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_MIXER_AGC,
							mMixerAgcCheckBox.isSelected() ? 1 : 0);
						getMixerGainSlider().setEnabled(!mMixerAgcCheckBox.isSelected() &&
							getTuner().getController().hasCapability(HydraSdrNative.CAP_MIXER_GAIN));
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting Mixer AGC", e1);
					}
				}
			});
		}
		return mMixerAgcCheckBox;
	}

	private JCheckBox getFilterAgcCheckBox()
	{
		if(mFilterAgcCheckBox == null)
		{
			mFilterAgcCheckBox = new JCheckBox("AGC Filter:");
			mFilterAgcCheckBox.setEnabled(false);
			mFilterAgcCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_FILTER_AGC,
							mFilterAgcCheckBox.isSelected() ? 1 : 0);
						getFilterGainSlider().setEnabled(!mFilterAgcCheckBox.isSelected() &&
							getTuner().getController().hasCapability(HydraSdrNative.CAP_FILTER_GAIN));
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting Filter AGC", e1);
					}
				}
			});
		}
		return mFilterAgcCheckBox;
	}

	private JCheckBox getBiasTCheckBox()
	{
		if(mBiasTCheckBox == null)
		{
			mBiasTCheckBox = new JCheckBox("Bias-T");
			mBiasTCheckBox.setEnabled(false);
			mBiasTCheckBox.setToolTipText("Enable Bias-T power output for active antennas");
			mBiasTCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setBiasT(mBiasTCheckBox.isSelected());
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting Bias-T", e1);
						JOptionPane.showMessageDialog(mBiasTCheckBox,
							"Couldn't set Bias-T: " + e1.getMessage());
					}
				}
			});
		}
		return mBiasTCheckBox;
	}

	private JLabel getLnaGainValueLabel()
	{
		if(mLnaGainValueLabel == null)
		{
			mLnaGainValueLabel = new JLabel("0");
			mLnaGainValueLabel.setEnabled(false);
		}
		return mLnaGainValueLabel;
	}

	private JSlider getLnaGainSlider()
	{
		if(mLnaGainSlider == null)
		{
			mLnaGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 14, 0);
			mLnaGainSlider.setEnabled(false);
			mLnaGainSlider.setMajorTickSpacing(1);
			mLnaGainSlider.setPaintTicks(true);
			mLnaGainSlider.addChangeListener(event ->
			{
				int gain = mLnaGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_LNA, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set LNA gain to:" + gain, e);
						JOptionPane.showMessageDialog(mLnaGainSlider,
							"Couldn't set LNA gain value to " + gain);
					}
				}
				getLnaGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mLnaGainSlider;
	}

	private JLabel getRfGainValueLabel()
	{
		if(mRfGainValueLabel == null)
		{
			mRfGainValueLabel = new JLabel("0");
			mRfGainValueLabel.setEnabled(false);
		}
		return mRfGainValueLabel;
	}

	private JSlider getRfGainSlider()
	{
		if(mRfGainSlider == null)
		{
			mRfGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 0);
			mRfGainSlider.setEnabled(false);
			mRfGainSlider.setMajorTickSpacing(1);
			mRfGainSlider.setPaintTicks(true);
			mRfGainSlider.addChangeListener(event ->
			{
				int gain = mRfGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_RF, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set RF gain to:" + gain, e);
						JOptionPane.showMessageDialog(mRfGainSlider,
							"Couldn't set RF gain value to " + gain);
					}
				}
				getRfGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mRfGainSlider;
	}

	private JLabel getMixerGainValueLabel()
	{
		if(mMixerGainValueLabel == null)
		{
			mMixerGainValueLabel = new JLabel("0");
			mMixerGainValueLabel.setEnabled(false);
		}
		return mMixerGainValueLabel;
	}

	private JSlider getMixerGainSlider()
	{
		if(mMixerGainSlider == null)
		{
			mMixerGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 0);
			mMixerGainSlider.setEnabled(false);
			mMixerGainSlider.setMajorTickSpacing(1);
			mMixerGainSlider.setPaintTicks(true);
			mMixerGainSlider.addChangeListener(event ->
			{
				int gain = mMixerGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_MIXER, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set Mixer gain to:" + gain, e);
						JOptionPane.showMessageDialog(mMixerGainSlider,
							"Couldn't set Mixer gain value to " + gain);
					}
				}
				getMixerGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mMixerGainSlider;
	}

	private JLabel getFilterGainValueLabel()
	{
		if(mFilterGainValueLabel == null)
		{
			mFilterGainValueLabel = new JLabel("0");
			mFilterGainValueLabel.setEnabled(false);
		}
		return mFilterGainValueLabel;
	}

	private JSlider getFilterGainSlider()
	{
		if(mFilterGainSlider == null)
		{
			mFilterGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 0);
			mFilterGainSlider.setEnabled(false);
			mFilterGainSlider.setMajorTickSpacing(1);
			mFilterGainSlider.setPaintTicks(true);
			mFilterGainSlider.addChangeListener(event ->
			{
				int gain = mFilterGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_FILTER, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set Filter gain to:" + gain, e);
						JOptionPane.showMessageDialog(mFilterGainSlider,
							"Couldn't set Filter gain value to " + gain);
					}
				}
				getFilterGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mFilterGainSlider;
	}

	private JLabel getVgaGainLabel()
	{
		if(mVgaGainLabel == null)
		{
			mVgaGainLabel = new JLabel("IF:");
		}
		return mVgaGainLabel;
	}

	private JLabel getVgaGainValueLabel()
	{
		if(mVgaGainValueLabel == null)
		{
			mVgaGainValueLabel = new JLabel("0");
			mVgaGainValueLabel.setEnabled(false);
		}
		return mVgaGainValueLabel;
	}

	private JSlider getVgaGainSlider()
	{
		if(mVgaGainSlider == null)
		{
			mVgaGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 0);
			mVgaGainSlider.setEnabled(false);
			mVgaGainSlider.setMajorTickSpacing(1);
			mVgaGainSlider.setPaintTicks(true);
			mVgaGainSlider.addChangeListener(event ->
			{
				int gain = mVgaGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_VGA, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set VGA gain to:" + gain, e);
						JOptionPane.showMessageDialog(mVgaGainSlider,
							"Couldn't set IF gain value to " + gain);
					}
				}
				getVgaGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mVgaGainSlider;
	}

	private JLabel getMasterGainLabel()
	{
		if(mMasterGainLabel == null)
		{
			mMasterGainLabel = new JLabel("Master:");
		}
		return mMasterGainLabel;
	}

	private JLabel getMasterGainValueLabel()
	{
		if(mMasterGainValueLabel == null)
		{
			mMasterGainValueLabel = new JLabel("0");
			mMasterGainValueLabel.setEnabled(false);
		}
		return mMasterGainValueLabel;
	}

	private JSlider getMasterGainSlider()
	{
		if(mMasterGainSlider == null)
		{
			mMasterGainSlider = new JSlider(JSlider.HORIZONTAL, 1, 22, 14);
			mMasterGainSlider.setEnabled(false);
			mMasterGainSlider.setMajorTickSpacing(1);
			mMasterGainSlider.setPaintTicks(true);
			mMasterGainSlider.addChangeListener(event ->
			{
				int value = mMasterGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						int mode = getGainModeCombo().getSelectedIndex();
						if(mode == 0)
						{
							getTuner().getController().setGain(
								HydraSdrNative.GAIN_TYPE_LINEARITY, value);
						}
						else if(mode == 1)
						{
							getTuner().getController().setGain(
								HydraSdrNative.GAIN_TYPE_SENSITIVITY, value);
						}
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set master gain to:" + value, e);
						JOptionPane.showMessageDialog(mMasterGainSlider,
							"Couldn't set gain value to " + value);
					}
				}
				getMasterGainValueLabel().setText(String.valueOf(value));
			});
		}
		return mMasterGainSlider;
	}

	private JComboBox<String> getGainModeCombo()
	{
		if(mGainModeCombo == null)
		{
			mGainModeCombo = new JComboBox<>(GAIN_MODES);
			mGainModeCombo.setEnabled(false);
			mGainModeCombo.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					HydraSdrTunerController controller = getTuner().getController();
					int requestedMode = mGainModeCombo.getSelectedIndex();
					int mode = getSupportedGainMode(requestedMode,
						controller.hasCapability(HydraSdrNative.CAP_LINEARITY_GAIN),
						controller.hasCapability(HydraSdrNative.CAP_SENSITIVITY_GAIN));
					if(mode != requestedMode)
					{
						setLoading(true);
						mGainModeCombo.setSelectedIndex(mode);
						setLoading(false);
					}
					mLog.info("Gain mode changed to: " + GAIN_MODES[mode] + " (" + mode + ")");

					/* Apply the mode change to the device */
					try
					{
						if(mode == 0)
						{
							/* Linearity: disable supported AGCs and apply the saved preset. */
							disableSupportedAgc(controller);
							if(controller.hasCapability(HydraSdrNative.CAP_LINEARITY_GAIN))
							{
								int value = hasConfiguration() ? getConfiguration().getLinearityGain() : 14;
								controller.setGain(HydraSdrNative.GAIN_TYPE_LINEARITY, value > 0 ? value : 14);
							}
						}
						else if(mode == 1)
						{
							/* Sensitivity: disable supported AGCs and apply the saved preset. */
							disableSupportedAgc(controller);
							if(controller.hasCapability(HydraSdrNative.CAP_SENSITIVITY_GAIN))
							{
								int value = hasConfiguration() ? getConfiguration().getSensitivityGain() : 10;
								controller.setGain(HydraSdrNative.GAIN_TYPE_SENSITIVITY, value > 0 ? value : 10);
							}
						}
						else if(hasConfiguration())
						{
							/* Restore only the saved custom settings supported by this device. */
							applySupportedCustomGains(controller);
						}
					}
					catch(Exception ex)
					{
						mLog.error("Error applying gain mode", ex);
					}

					setLoading(true);
					try
					{
						updateGainControls(false);
					}
					finally
					{
						setLoading(false);
					}
					save();
				}
			});
		}
		return mGainModeCombo;
	}

	private void disableSupportedAgc(HydraSdrTunerController controller) throws SourceException
	{
		if(controller.hasCapability(HydraSdrNative.CAP_LNA_AGC))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_LNA_AGC, 0);
		}
		if(controller.hasCapability(HydraSdrNative.CAP_RF_AGC))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_RF_AGC, 0);
		}
		if(controller.hasCapability(HydraSdrNative.CAP_MIXER_AGC))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_MIXER_AGC, 0);
		}
		if(controller.hasCapability(HydraSdrNative.CAP_FILTER_AGC))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_FILTER_AGC, 0);
		}
	}

	private void applySupportedCustomGains(HydraSdrTunerController controller) throws SourceException
	{
		if(controller.hasCapability(HydraSdrNative.CAP_LNA_AGC))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_LNA_AGC,
				getConfiguration().isLnaAgc() ? 1 : 0);
		}
		if(controller.hasCapability(HydraSdrNative.CAP_RF_AGC))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_RF_AGC,
				getConfiguration().isRfAgc() ? 1 : 0);
		}
		if(controller.hasCapability(HydraSdrNative.CAP_MIXER_AGC))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_MIXER_AGC,
				getConfiguration().isMixerAgc() ? 1 : 0);
		}
		if(controller.hasCapability(HydraSdrNative.CAP_FILTER_AGC))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_FILTER_AGC,
				getConfiguration().isFilterAgc() ? 1 : 0);
		}
		if(controller.hasCapability(HydraSdrNative.CAP_LNA_GAIN))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_LNA, getConfiguration().getLnaGain());
		}
		if(controller.hasCapability(HydraSdrNative.CAP_RF_GAIN) && getConfiguration().getRfGain() >= 0)
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_RF, getConfiguration().getRfGain());
		}
		if(controller.hasCapability(HydraSdrNative.CAP_MIXER_GAIN))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_MIXER, getConfiguration().getMixerGain());
		}
		if(controller.hasCapability(HydraSdrNative.CAP_FILTER_GAIN) && getConfiguration().getFilterGain() >= 0)
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_FILTER, getConfiguration().getFilterGain());
		}
		if(controller.hasCapability(HydraSdrNative.CAP_VGA_GAIN))
		{
			controller.setGain(HydraSdrNative.GAIN_TYPE_VGA, getConfiguration().getVgaGain());
		}
	}

	private JComboBox<HydraSdrSampleRate> getSampleRateCombo()
	{
		if(mSampleRateCombo == null)
		{
			mSampleRateCombo = new JComboBox<>();
			mSampleRateCombo.setEnabled(false);
			mSampleRateCombo.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					HydraSdrSampleRate rate = (HydraSdrSampleRate)mSampleRateCombo.getSelectedItem();
					if(rate != null)
					{
						try
						{
							getTuner().getController().setSampleRate(rate);
							adjustForSampleRate(rate.getRate());
							save();
						}
						catch(Exception e1)
						{
							JOptionPane.showMessageDialog(HydraSdrTunerEditor.this,
								"Couldn't set sample rate to " + rate.getLabel());
							mLog.error("Error setting sample rate", e1);
						}
					}
				}
			});
		}
		return mSampleRateCombo;
	}

	private JButton getTunerInfoButton()
	{
		if(mTunerInfoButton == null)
		{
			mTunerInfoButton = new JButton("Info");
			mTunerInfoButton.setEnabled(false);
			mTunerInfoButton.addActionListener(e -> JOptionPane.showMessageDialog(
				HydraSdrTunerEditor.this, getTunerInfo(), "Tuner Info",
				JOptionPane.INFORMATION_MESSAGE));
		}
		return mTunerInfoButton;
	}

	static int getSupportedGainMode(int requestedMode, boolean hasLinearityGain, boolean hasSensitivityGain)
	{
		if(requestedMode < HydraSdrTunerController.GAIN_MODE_LINEARITY ||
			requestedMode > HydraSdrTunerController.GAIN_MODE_CUSTOM ||
			(requestedMode == 0 && !hasLinearityGain) || (requestedMode == 1 && !hasSensitivityGain))
		{
			return HydraSdrTunerController.GAIN_MODE_CUSTOM;
		}
		return requestedMode;
	}

	/**
	 * Updates gain control enabled/disabled state based on gain mode.
	 * Reads the current mode from the combo box (user selection), not from saved config.
	 */
	private void updateGainControls()
	{
		updateGainControls(isLoading());
	}

	private void updateGainControls(boolean loadConfiguredMode)
	{
		if(hasTuner())
		{
			HydraSdrTunerController controller = getTuner().getController();
			int mode = getGainModeCombo().getSelectedIndex();
			if(mode < 0 || loadConfiguredMode && hasConfiguration())
			{
				mode = hasConfiguration() ? getConfiguration().getGainMode() : 0;
			}
			mode = getSupportedGainMode(mode,
				controller.hasCapability(HydraSdrNative.CAP_LINEARITY_GAIN),
				controller.hasCapability(HydraSdrNative.CAP_SENSITIVITY_GAIN));
			boolean isCustom = (mode == HydraSdrTunerController.GAIN_MODE_CUSTOM);

			getGainModeCombo().setEnabled(true);
			getGainModeCombo().setSelectedIndex(mode);
			boolean hasPresetGain = mode == 0 ? controller.hasCapability(HydraSdrNative.CAP_LINEARITY_GAIN) :
				controller.hasCapability(HydraSdrNative.CAP_SENSITIVITY_GAIN);
			boolean hasLnaAgc = controller.hasCapability(HydraSdrNative.CAP_LNA_AGC);
			boolean hasRfAgc = controller.hasCapability(HydraSdrNative.CAP_RF_AGC);
			boolean hasMixerAgc = controller.hasCapability(HydraSdrNative.CAP_MIXER_AGC);
			boolean hasFilterAgc = controller.hasCapability(HydraSdrNative.CAP_FILTER_AGC);
			boolean hasLnaGain = controller.hasCapability(HydraSdrNative.CAP_LNA_GAIN);
			boolean hasRfGain = controller.hasCapability(HydraSdrNative.CAP_RF_GAIN);
			boolean hasMixerGain = controller.hasCapability(HydraSdrNative.CAP_MIXER_GAIN);
			boolean hasFilterGain = controller.hasCapability(HydraSdrNative.CAP_FILTER_GAIN);
			boolean hasVgaGain = controller.hasCapability(HydraSdrNative.CAP_VGA_GAIN);

			getMasterGainLabel().setEnabled(!isCustom && hasPresetGain);
			getMasterGainSlider().setEnabled(!isCustom && hasPresetGain);
			getMasterGainValueLabel().setEnabled(!isCustom && hasPresetGain);
			getVgaGainLabel().setEnabled(isCustom && hasVgaGain);
			getVgaGainSlider().setEnabled(isCustom && hasVgaGain);
			getVgaGainValueLabel().setEnabled(isCustom && hasVgaGain);
			getLnaAgcCheckBox().setEnabled(isCustom && hasLnaAgc);
			getLnaGainSlider().setEnabled(isCustom && hasLnaGain &&
				(!hasLnaAgc || !(hasConfiguration() && getConfiguration().isLnaAgc())));
			getLnaGainValueLabel().setEnabled(isCustom && hasLnaGain);
			getRfAgcCheckBox().setEnabled(isCustom && hasRfAgc);
			getRfGainSlider().setEnabled(isCustom && hasRfGain &&
				(!hasRfAgc || !(hasConfiguration() && getConfiguration().isRfAgc())));
			getRfGainValueLabel().setEnabled(isCustom && hasRfGain);
			getMixerAgcCheckBox().setEnabled(isCustom && hasMixerAgc);
			getMixerGainSlider().setEnabled(isCustom && hasMixerGain &&
				(!hasMixerAgc || !(hasConfiguration() && getConfiguration().isMixerAgc())));
			getMixerGainValueLabel().setEnabled(isCustom && hasMixerGain);
			getFilterAgcCheckBox().setEnabled(isCustom && hasFilterAgc);
			getFilterGainSlider().setEnabled(isCustom && hasFilterGain &&
				(!hasFilterAgc || !(hasConfiguration() && getConfiguration().isFilterAgc())));
			getFilterGainValueLabel().setEnabled(isCustom && hasFilterGain);

			/* Apply device ranges before restoring values so Swing does not clamp valid saved gains. */
			updateGainRangesFromDevice();

			if(hasConfiguration())
			{
				if(isCustom)
				{
					getVgaGainSlider().setValue(getConfiguration().getVgaGain());
					getLnaGainSlider().setValue(getConfiguration().getLnaGain());
					if(getConfiguration().getRfGain() >= 0)
					{
						getRfGainSlider().setValue(getConfiguration().getRfGain());
					}
					getMixerGainSlider().setValue(getConfiguration().getMixerGain());
					if(getConfiguration().getFilterGain() >= 0)
					{
						getFilterGainSlider().setValue(getConfiguration().getFilterGain());
					}
					getFilterAgcCheckBox().setSelected(getConfiguration().isFilterAgc());
					getMixerAgcCheckBox().setSelected(getConfiguration().isMixerAgc());
					getRfAgcCheckBox().setSelected(getConfiguration().isRfAgc());
					getLnaAgcCheckBox().setSelected(getConfiguration().isLnaAgc());
				}
				else if(mode == 0)
				{
					int lin = getConfiguration().getLinearityGain();
					getMasterGainSlider().setValue(lin > 0 ? lin : 14);
				}
				else
				{
					int sens = getConfiguration().getSensitivityGain();
					getMasterGainSlider().setValue(sens > 0 ? sens : 10);
				}
			}

		}
		else
		{
			getGainModeCombo().setEnabled(false);
			getMasterGainLabel().setEnabled(false);
			getMasterGainSlider().setEnabled(false);
			getMasterGainSlider().setValue(1);
			getMasterGainValueLabel().setEnabled(false);
			getVgaGainLabel().setEnabled(false);
			getVgaGainSlider().setEnabled(false);
			getVgaGainSlider().setValue(0);
			getVgaGainValueLabel().setEnabled(false);
			getLnaAgcCheckBox().setEnabled(false);
			getLnaAgcCheckBox().setSelected(false);
			getLnaGainSlider().setEnabled(false);
			getLnaGainSlider().setValue(0);
			getLnaGainValueLabel().setEnabled(false);
			getRfAgcCheckBox().setEnabled(false);
			getRfAgcCheckBox().setSelected(false);
			getRfGainSlider().setEnabled(false);
			getRfGainSlider().setValue(0);
			getRfGainValueLabel().setEnabled(false);
			getMixerAgcCheckBox().setEnabled(false);
			getMixerAgcCheckBox().setSelected(false);
			getMixerGainSlider().setEnabled(false);
			getMixerGainSlider().setValue(0);
			getMixerGainValueLabel().setEnabled(false);
			getFilterAgcCheckBox().setEnabled(false);
			getFilterAgcCheckBox().setSelected(false);
			getFilterGainSlider().setEnabled(false);
			getFilterGainSlider().setValue(0);
			getFilterGainValueLabel().setEnabled(false);
		}
	}

	/**
	 * Queries device for actual gain ranges and updates slider min/max.
	 */
	private void updateGainRangesFromDevice()
	{
		if(!hasTuner())
		{
			return;
		}

		HydraSdrTunerController ctrl = getTuner().getController();

		if(ctrl.hasCapability(HydraSdrNative.CAP_LNA_GAIN))
		{
			updateSliderRange(getLnaGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_LNA));
		}
		if(ctrl.hasCapability(HydraSdrNative.CAP_RF_GAIN))
		{
			updateSliderRange(getRfGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_RF));
		}
		if(ctrl.hasCapability(HydraSdrNative.CAP_MIXER_GAIN))
		{
			updateSliderRange(getMixerGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_MIXER));
		}
		if(ctrl.hasCapability(HydraSdrNative.CAP_FILTER_GAIN))
		{
			updateSliderRange(getFilterGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_FILTER));
		}
		if(ctrl.hasCapability(HydraSdrNative.CAP_VGA_GAIN))
		{
			updateSliderRange(getVgaGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_VGA));
		}
	}

	private void updateSliderRange(JSlider slider, int[] gainInfo)
	{
		if(gainInfo != null && gainInfo.length > HydraSdrNative.GAIN_INFO_DEFAULT)
		{
			slider.setMinimum(gainInfo[HydraSdrNative.GAIN_INFO_MIN]);
			slider.setMaximum(gainInfo[HydraSdrNative.GAIN_INFO_MAX]);
			slider.setValue(gainInfo[HydraSdrNative.GAIN_INFO_DEFAULT]);
		}
	}

	@Override
	public void save()
	{
		if(hasConfiguration() && !isLoading())
		{
			getConfiguration().setFrequency(getFrequencyControl().getFrequency());
			getConfiguration().setMinimumFrequency(getMinimumFrequencyTextField().getFrequency());
			getConfiguration().setMaximumFrequency(getMaximumFrequencyTextField().getFrequency());
			double value = ((SpinnerNumberModel)getFrequencyCorrectionSpinner()
				.getModel()).getNumber().doubleValue();
			getConfiguration().setFrequencyCorrection(value);
			getConfiguration().setAutoPPMCorrectionEnabled(getAutoPPMCheckBox().isSelected());

			HydraSdrSampleRate rate = (HydraSdrSampleRate)getSampleRateCombo().getSelectedItem();
			if(rate != null)
			{
				getConfiguration().setSampleRate(rate.getRate());
			}

			int mode = getGainModeCombo().getSelectedIndex();
			HydraSdrTunerController controller = getTuner().getController();
			mode = getSupportedGainMode(mode,
				controller.hasCapability(HydraSdrNative.CAP_LINEARITY_GAIN),
				controller.hasCapability(HydraSdrNative.CAP_SENSITIVITY_GAIN));
			getConfiguration().setGainMode(mode);

			if(mode == 0)
			{
				getConfiguration().setLinearityGain(getMasterGainSlider().getValue());
			}
			else if(mode == 1)
			{
				getConfiguration().setSensitivityGain(getMasterGainSlider().getValue());
			}
			else
			{
				/* Custom mode: save individual gain values while preserving both presets. */
				getConfiguration().setVgaGain(getVgaGainSlider().getValue());
				getConfiguration().setMixerGain(getMixerGainSlider().getValue());
				getConfiguration().setLnaGain(getLnaGainSlider().getValue());
				if(controller.hasCapability(HydraSdrNative.CAP_RF_GAIN))
				{
					getConfiguration().setRfGain(getRfGainSlider().getValue());
				}
				if(controller.hasCapability(HydraSdrNative.CAP_FILTER_GAIN))
				{
					getConfiguration().setFilterGain(getFilterGainSlider().getValue());
				}
				getConfiguration().setMixerAgc(getMixerAgcCheckBox().isSelected());
				getConfiguration().setLnaAgc(getLnaAgcCheckBox().isSelected());
				if(controller.hasCapability(HydraSdrNative.CAP_RF_AGC))
				{
					getConfiguration().setRfAgc(getRfAgcCheckBox().isSelected());
				}
				if(controller.hasCapability(HydraSdrNative.CAP_FILTER_AGC))
				{
					getConfiguration().setFilterAgc(getFilterAgcCheckBox().isSelected());
				}
			}
			getConfiguration().setBiasT(getBiasTCheckBox().isSelected());
			saveConfiguration();
		}
	}

	@Override
	public void setTunerLockState(boolean locked)
	{
		getFrequencyPanel().updateControls();
		getSampleRateCombo().setEnabled(!locked);
		if(hasTuner() && getTuner().getController().isLockedSampleRate())
		{
			getSampleRateCombo().setToolTipText(
				"Sample Rate is locked. Disable decoding channels to unlock.");
		}
		else
		{
			getSampleRateCombo().setToolTipText("Select a sample rate for the tuner");
		}
	}

	private String getTunerInfo()
	{
		if(getDiscoveredTuner().getTunerStatus() == TunerStatus.ERROR)
		{
			return getDiscoveredTuner().getErrorMessage();
		}

		if(hasTuner())
		{
			StringBuilder sb = new StringBuilder();
			HydraSdrDeviceInfo info = getTuner().getController().getDeviceInfo();

			sb.append("<html><h3>HydraSDR Tuner</h3>");

			if(info != null)
			{
				sb.append("<b>Board: </b>").append(info.getBoardName()).append("<br>");
				sb.append("<b>Serial: </b>").append(info.getSerialNumber()).append("<br>");
				sb.append("<b>Firmware: </b>").append(info.getFirmwareVersion()).append("<br>");
				sb.append("<b>Part: </b>").append(info.getPartNumber()).append("<br>");
				sb.append("<b>Freq Range: </b>").append(info.getMinFrequency() / 1e6)
					.append(" - ").append(info.getMaxFrequency() / 1e6).append(" MHz<br>");
				sb.append("<b>Capabilities: </b>0x")
					.append(Integer.toHexString(info.getCapabilities())).append("<br>");

				float temp = getTuner().getController().getTemperature();
				if(!Float.isNaN(temp))
				{
					sb.append("<b>Temperature: </b>").append(String.format("%.1f", temp))
						.append(" C<br>");
				}
			}
			else
			{
				sb.append("Device info not available<br>");
			}

			/* Library version */
			if(HydraSdrNative.isLoaded())
			{
				int[] ver = HydraSdrNative.getLibVersion();
				if(ver != null && ver.length >= 3)
				{
					sb.append("<b>Library: </b>").append(ver[0]).append(".")
						.append(ver[1]).append(".").append(ver[2]).append("<br>");
				}
			}

			/* Performance stats */
			String stats = getTuner().getController().getPerformanceStats();
			sb.append("<br><b>--- Performance ---</b><br>");
			sb.append("<pre>").append(stats).append("</pre>");

			return sb.toString();
		}

		return null;
	}
}
