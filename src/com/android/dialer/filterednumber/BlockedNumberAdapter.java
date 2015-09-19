/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.dialer.filterednumber;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.R;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.util.PhoneNumberUtil;

public class BlockedNumberAdapter extends SimpleCursorAdapter {

    private Context mContext;
    private ContactInfoHelper mContactInfoHelper;
    private Resources mResources;
    private BidiFormatter mBidiFormatter = BidiFormatter.getInstance();
    private ContactPhotoManager mContactPhotoManager;
    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    public BlockedNumberAdapter(Context context,
                                FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler) {
        super(context, R.layout.blocked_number_item, null, new String[]{}, new int[]{}, 0);
        mContext = context;
        mContactInfoHelper = new ContactInfoHelper(context, GeoUtil.getCurrentCountryIso(context));
        mContactPhotoManager = ContactPhotoManager.getInstance(context);
        mResources = context.getResources();
        mFilteredNumberAsyncQueryHandler = filteredNumberAsyncQueryHandler;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        super.bindView(view, context, cursor);
        final TextView callerName = (TextView) view.findViewById(R.id.caller_name);
        final TextView callerNumber = (TextView) view.findViewById(R.id.caller_number);
        final View deleteNumber = view.findViewById(R.id.delete_button);
        final QuickContactBadge quickContactBadge =
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo);
        quickContactBadge.setOverlay(null);
        quickContactBadge.setPrioritizedMimeType(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);

        final Integer id = cursor.getInt(cursor.getColumnIndex(FilteredNumberColumns._ID));
        final String countryIso = cursor.getString(cursor.getColumnIndex(
                FilteredNumberColumns.COUNTRY_ISO));
        final String number = cursor.getString(cursor.getColumnIndex(FilteredNumberColumns.NUMBER));
        final String normalizedNumber = cursor.getString(cursor.getColumnIndex(
                FilteredNumberColumns.NORMALIZED_NUMBER));
        final ContactInfo info = mContactInfoHelper.lookupNumber(number, countryIso);
        final CharSequence locationOrType = getNumberTypeOrLocation(info);
        final String displayNumber = getDisplayNumber(info);
        final String displayNumberStr = mBidiFormatter.unicodeWrap(
                displayNumber.toString(), TextDirectionHeuristics.LTR);

        deleteNumber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FilterNumberDialogFragment newFragment =
                        FilterNumberDialogFragment.newInstance(id, normalizedNumber, number,
                                countryIso, displayNumber);
                newFragment.setQueryHandler(mFilteredNumberAsyncQueryHandler);
                newFragment.setParentView(view);
                newFragment.show(((Activity) mContext).getFragmentManager(),
                        FilterNumberDialogFragment.BLOCK_DIALOG_FRAGMENT);
            }
        });

        String nameForDefaultImage;
        if (!TextUtils.isEmpty(info.name)) {
            nameForDefaultImage = info.name;
            callerName.setText(info.name);
            callerNumber.setText(locationOrType + " " + displayNumberStr);
        } else {
            nameForDefaultImage = displayNumber;
            callerName.setText(displayNumberStr);
            if (!TextUtils.isEmpty(locationOrType)) {
                callerNumber.setText(locationOrType);
                callerNumber.setVisibility(View.VISIBLE);
            } else {
                callerNumber.setVisibility(View.GONE);
            }
        }
        loadContactPhoto(info, nameForDefaultImage, quickContactBadge);
    }

    private void loadContactPhoto(ContactInfo info, String displayName, QuickContactBadge badge) {
        final String lookupKey = info.lookupUri == null
                ? null : UriUtils.getLookupKeyFromUri(info.lookupUri);
        final int contactType = mContactInfoHelper.isBusiness(info.sourceType)
                ? ContactPhotoManager.TYPE_BUSINESS : ContactPhotoManager.TYPE_DEFAULT;
        final DefaultImageRequest request = new DefaultImageRequest(displayName, lookupKey,
                contactType, true /* isCircular */);
        badge.assignContactUri(info.lookupUri);
        badge.setContentDescription(
                mResources.getString(R.string.description_contact_details, displayName));
        mContactPhotoManager.loadDirectoryPhoto(badge, info.photoUri,
                false /* darkTheme */, true /* isCircular */, request);
    }

    private String getDisplayNumber(ContactInfo info) {
        if (!TextUtils.isEmpty(info.formattedNumber)) {
            return info.formattedNumber;
        } else if (!TextUtils.isEmpty(info.number)) {
            return info.number;
        } else {
            return "";
        }
    }

    private CharSequence getNumberTypeOrLocation(ContactInfo info) {
        if (!TextUtils.isEmpty(info.name)) {
            return ContactsContract.CommonDataKinds.Phone.getTypeLabel(mResources, info.type,
                    info.label);
        } else {
            return PhoneNumberUtil.getGeoDescription(mContext, info.number);
        }
    }
}