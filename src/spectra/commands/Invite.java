/*
 * Copyright 2016 John Grosh (jagrosh).
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
package spectra.commands;

import net.dv8tion.jda.events.message.MessageReceivedEvent;
import net.dv8tion.jda.utils.ApplicationUtil;
import spectra.Command;
import spectra.Sender;
import spectra.SpConst;

/**
 *
 * @author John Grosh (jagrosh)
 */
public class Invite extends Command {
    public Invite()
    {
        this.command = "invite";
        this.help = "invite "+SpConst.BOTNAME+" to your server";
        this.longhelp = "This command provides a link for inviting "+SpConst.BOTNAME+" to a server, as well providing an invite to jagrosh's bot server.";
    }

    @Override
    protected boolean execute(Object[] args, MessageReceivedEvent event) {
        Sender.sendResponse("Hi! If you want me on your server, simply click this link, select a server where you have the \"Manage Server\" permission, and authorize!\n"
                +"https://discordapp.com/oauth2/authorize?client_id="+ApplicationUtil.getApplicationId(event.getJDA())+"&scope=bot&permissions=297921599\n"
                //+ "If you give me a role called \""+botName+"\" that is my color-controlling role, and the \"Manage Roles\" permission, I'll adjust my username color to match my image whenever it changes!\n"
                + "Also, check out **"+event.getJDA().getGuildById(SpConst.JAGZONE_ID).getName()+"**: "+SpConst.JAGZONE_INVITE, event);
        return true;
    }
}
