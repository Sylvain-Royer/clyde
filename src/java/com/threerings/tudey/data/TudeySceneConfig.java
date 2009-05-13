//
// $Id$
//
// Clyde library - tools for developing networked games
// Copyright (C) 2005-2009 Three Rings Design, Inc.
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.tudey.data;

import com.threerings.crowd.client.PlaceController;
import com.threerings.crowd.data.PlaceConfig;

import com.threerings.export.Exportable;
import com.threerings.util.Copyable;
import com.threerings.util.DeepUtil;

import com.threerings.tudey.client.TudeySceneController;

/**
 * Place configuration for Tudey scenes.
 */
public class TudeySceneConfig extends PlaceConfig
    implements Exportable, Cloneable, Copyable
{
    /**
     * Returns the interval at which clients transmit their input frames.
     */
    public int getTransmitInterval ()
    {
        return 100;
    }

    // documentation inherited from interface Copyable
    public Object copy (Object dest)
    {
        return DeepUtil.copy(this, dest);
    }

    @Override // documentation inherited
    public PlaceController createController ()
    {
        return new TudeySceneController();
    }

    @Override // documentation inherited
    public String getManagerClassName ()
    {
        return "com.threerings.tudey.server.TudeySceneManager";
    }

    @Override // documentation inherited
    public Object clone ()
    {
        return copy(null);
    }

    @Override // documentation inherited
    public boolean equals (Object other)
    {
        return DeepUtil.equals(this, other);
    }

    @Override // documentation inherited
    public int hashCode ()
    {
        return DeepUtil.hashCode(this);
    }
}
