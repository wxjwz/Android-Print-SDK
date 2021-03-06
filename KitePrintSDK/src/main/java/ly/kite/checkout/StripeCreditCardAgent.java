/*****************************************************
 *
 * StripeCreditCardFragment.java
 *
 *
 * Modified MIT License
 *
 * Copyright (c) 2010-2015 Kite Tech Ltd. https://www.kite.ly
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The software MAY ONLY be used with the Kite Tech Ltd platform and MAY NOT be modified
 * to be used with any competitor platforms. This means the software MAY NOT be modified 
 * to place orders with any competitors to Kite Tech Ltd, all orders MUST go through the
 * Kite Tech Ltd platform servers. 
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 *****************************************************/

///// Package Declaration /////

package ly.kite.checkout;


///// Import(s) /////

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;
import com.stripe.exception.AuthenticationException;

import ly.kite.KiteSDK;
import ly.kite.app.IndeterminateProgressDialogFragment;
import ly.kite.R;
import ly.kite.catalogue.SingleCurrencyAmounts;
import ly.kite.ordering.Order;


///// Class Declaration /////

/*****************************************************
 *
 * This abstract class is the parent of fragments that
 * collect credit card details.
 *
 *****************************************************/
public class StripeCreditCardAgent extends ACreditCardDialogFragment implements ICreditCardAgent
  {
  ////////// Static Constant(s) //////////

  @SuppressWarnings( "unused" )
  static private final String  TAG = "StripeCreditCardFrag.";


  ////////// Static Variable(s) //////////


  ////////// Member Variable(s) //////////

  private Context                 mContext;
  private DefaultPaymentFragment  mPaymentFragment;
  private Order                   mOrder;
  private SingleCurrencyAmounts   mSingleCurrencyAmount;

  private String                  mLocalisedStripeErrorMessage;


  ////////// Static Initialiser(s) //////////


  ////////// Static Method(s) //////////


  ////////// Constructor(s) //////////


  ////////// ACreditCardDialogFragment Method(s) //////////

  /*****************************************************
   *
   * Called when the fragment is attached to an activity.
   *
   *****************************************************/
  @Override
  public void onAttach( Activity activity )
    {
    super.onAttach( activity );

    // We get the error message here, because we can't get it when we aren't attached to an activity.
    mLocalisedStripeErrorMessage = getString( R.string.stripe_error_retrieve_token );
    }


  ////////// ICreditCardFragment Method(s) //////////

  /*****************************************************
   *
   * Returns true if the agent uses PayPal to process
   * credit card payments.
   *
   *****************************************************/
  public boolean usesPayPal()
    {
    return ( false );
    }


  /*****************************************************
   *
   * Notifies the agent that the user has clicked on the
   * credit card payment button.
   *
   *****************************************************/
  @Override
  public void onPayClicked( Context context, DefaultPaymentFragment paymentFragment, Order order, SingleCurrencyAmounts singleCurrencyAmount )
    {
    mContext              = context;
    mPaymentFragment      = paymentFragment;
    mOrder                = order;
    mSingleCurrencyAmount = singleCurrencyAmount;

    // Since we are a subclass of a dialog fragment, display us
    // as a dialog.
    show( paymentFragment.getFragmentManager(), TAG );
    }


  /*****************************************************
   *
   * Passes an activity result to the agent.
   *
   *****************************************************/
  @Override
  public void onActivityResult( int requestCode, int resultCode, Intent data )
    {
    // We aren't expecting an activity result
    }


  ////////// Method(s) //////////

  /*****************************************************
   *
   * Called when the proceed button has been clicked.
   *
   *****************************************************/
  @Override
  protected void onProceed( String cardNumberString, String expiryMonthString, String expiryYearString, String cvvString )
    {
    // Do basic validation of card details

    if ( ! validateCard( cardNumberString, expiryMonthString, expiryYearString, cvvString ) ) return;


    // Create a Stripe card and perform additional validation. If it's OK, use it for payment.

    Card card = getValidatedCard( cardNumberString, expiryMonthString, expiryYearString, cvvString );

    if ( card != null )
      {
      onUseCard( card );
      }
    }


  /*****************************************************
   *
   * Returns a validated Stripe card, or null, if the card
   * could not be validated.
   *
   *****************************************************/
  private Card getValidatedCard( String cardNumberString, String expiryMonthString, String expiryYearString, String cvvString )
    {
    // Create a Stripe card and validate it

    try
      {
      Card card = new Card( cardNumberString, Integer.parseInt( expiryMonthString ), Integer.parseInt( expiryYearString ), cvvString );

      if ( ! card.validateNumber() )
        {
        onDisplayError( R.string.card_error_invalid_number );

        return ( null );
        }

      if ( ! card.validateExpiryDate() )
        {
        onDisplayError( R.string.card_error_invalid_expiry_date );

        return ( null );
        }

      if ( ! card.validateCVC() )
        {
        onDisplayError( R.string.card_error_invalid_cvv );

        return ( null );
        }


      return ( card );
      }
    catch ( Exception exception )
      {
      onDisplayError( getString( R.string.stripe_error_invalid_card_details ) + ": " + exception.getMessage() );
      }

    return ( null );
    }


  /*****************************************************
   *
   * Called when the card has been validated.
   *
   *****************************************************/
  private void onUseCard( Card card )
    {
    // Hide the dialog
    dismiss();


    // We can't call back to the activity because it has no concept of other credit cards. So
    // we need to do the processing ourselves, and then return the token as the payment id to the
    // Payment Activity.

    String stripePublicKey = KiteSDK.getInstance( mContext ).getStripePublicKey();

    Stripe stripe = null;

    try
      {
      stripe = new Stripe( stripePublicKey );
      }
    catch ( AuthenticationException ae )
      {
      Log.e( TAG, "Unable to create Stripe object", ae );

      // TODO: Display an error dialog
      Toast.makeText( mContext, getString( R.string.stripe_error_create_object ) + ": " + ae.getMessage(), Toast.LENGTH_LONG ).show();

      return;
      }


    // Show a progress dialog

    final IndeterminateProgressDialogFragment indeterminateProgressDialogFragment = IndeterminateProgressDialogFragment.newInstance( getActivity(), R.string.Processing_ );

    indeterminateProgressDialogFragment.show( getActivity() );


    // TODO: Get rid of this yucky anonymous inner class
    stripe.createToken
      (
      card,
      new TokenCallback()
        {
        public void onSuccess( Token token )
          {
          indeterminateProgressDialogFragment.dismiss();

          // Return the token to the activity

          if ( mContext instanceof PaymentActivity )
            {
            mPaymentFragment.submitOrderForPrinting( token.getId(), KiteSDK.getInstance( getActivity() ).getStripeAccountId(), PaymentMethod.CREDIT_CARD );
            }
          }

        public void onError( Exception exception )
          {
          indeterminateProgressDialogFragment.dismiss();

          Log.e( TAG, "Error retrieving token", exception );

          // TODO: Display error dialog
          // Show localised error message
          Toast.makeText( mContext, mLocalisedStripeErrorMessage + ": " + exception.getMessage(), Toast.LENGTH_LONG ).show();
          }
        }
      );
    }


  ////////// Inner Class(es) //////////

  }

